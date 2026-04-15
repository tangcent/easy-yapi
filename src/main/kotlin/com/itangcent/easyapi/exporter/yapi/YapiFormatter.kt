package com.itangcent.easyapi.exporter.yapi

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.model.path
import com.itangcent.easyapi.exporter.yapi.model.YapiApiDoc
import com.itangcent.easyapi.exporter.yapi.model.YapiFormParam
import com.itangcent.easyapi.exporter.yapi.model.YapiHeader
import com.itangcent.easyapi.exporter.yapi.model.YapiPathParam
import com.itangcent.easyapi.exporter.yapi.model.YapiQuery
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.model.ObjectModelJsonConverter

/**
 * Formatter for converting API endpoints to YAPI documentation format.
 * 
 * This formatter transforms the internal API model into YAPI's expected
 * document structure, supporting:
 * - Headers, query params, path params, and form data
 * - JSON Schema or JSON5 format for request/response bodies
 * - Mock data generation for examples
 * 
 * @param jsonSchemaBuilder Builder for creating JSON Schema from object models
 * @param reqBodyJson5 Whether to use JSON5 format for request body (false = JSON Schema)
 * @param resBodyJson5 Whether to use JSON5 format for response body (false = JSON Schema)
 * @param mockGenerator Optional generator for mock data examples
 */
class YapiFormatter(
    private val jsonSchemaBuilder: JsonSchemaBuilder = JsonSchemaBuilder(),
    private val reqBodyJson5: Boolean = false,
    private val resBodyJson5: Boolean = false,
    private val mockGenerator: MockDataGenerator? = MockDataGenerator(),
    private val autoFormatUrl: Boolean = true
) {
    /**
     * Legacy constructor for backward compatibility.
     * When a single useJson5 flag is provided, it applies to both request and response body.
     * 
     * @param jsonSchemaBuilder Builder for creating JSON Schema
     * @param useJson5 Whether to use JSON5 format for both request and response bodies
     * @param mockGenerator Optional generator for mock data
     */
    constructor(
        jsonSchemaBuilder: JsonSchemaBuilder = JsonSchemaBuilder(),
        useJson5: Boolean,
        mockGenerator: MockDataGenerator? = MockDataGenerator()
    ) : this(
        jsonSchemaBuilder,
        reqBodyJson5 = useJson5,
        resBodyJson5 = useJson5,
        mockGenerator = mockGenerator,
        autoFormatUrl = true
    )

    /**
     * Formats an API endpoint as a YAPI document.
     * Converts all parameters, headers, and body definitions to YAPI format.
     * 
     * @param endpoint The API endpoint to format
     * @return A YAPI document ready for upload
     */
    fun format(endpoint: ApiEndpoint): YapiApiDoc {
        val httpMeta = endpoint.httpMetadata
        val headers = (httpMeta?.headers ?: emptyList()).map {
            YapiHeader(
                name = it.name,
                value = it.value,
                desc = it.description,
                example = it.example ?: it.value,
                required = if (it.required) 1 else 0
            )
        }

        val parameters = httpMeta?.parameters ?: emptyList()
        val query = parameters
            .filter { it.binding == ParameterBinding.Query }
            .map {
                YapiQuery(
                    name = it.name,
                    value = it.defaultValue,
                    desc = it.description,
                    example = it.example ?: it.defaultValue,
                    required = if (it.required) 1 else 0
                )
            }

        val pathParams = parameters
            .filter { it.binding == ParameterBinding.Path }
            .map {
                YapiPathParam(
                    name = it.name,
                    example = it.example ?: it.defaultValue,
                    desc = it.description
                )
            }

        val formParams = parameters
            .filter { it.binding == ParameterBinding.Form }
            .map {
                YapiFormParam(
                    name = it.name,
                    example = it.example ?: it.defaultValue,
                    type = it.type.rawType(),
                    required = if (it.required) 1 else 0,
                    desc = it.description
                )
            }

        val requestBody = httpMeta?.body
        val hasJsonBody = requestBody != null || parameters.any { it.binding == ParameterBinding.Body }
        val hasFormBody = formParams.isNotEmpty()

        val reqBodyOther = if (requestBody != null) {
            if (reqBodyJson5) {
                formatAsJson5(requestBody)
            } else {
                jsonSchemaBuilder.build(requestBody)
            }
        } else if (parameters.any { it.binding == ParameterBinding.Body }) {
            val bodyParams = parameters.filter { it.binding == ParameterBinding.Body }
            if (reqBodyJson5) {
                // For body params with json5, build an object model and format as json5
                jsonSchemaBuilder.buildFromParameters(bodyParams)
            } else {
                jsonSchemaBuilder.buildFromParameters(bodyParams)
            }
        } else {
            null
        }

        val reqBodyType = when {
            hasJsonBody -> "json"
            hasFormBody -> "form"
            else -> null
        }

        // req_body_is_json_schema: true when we use JSON schema (not JSON5) for request body
        val reqBodyIsJsonSchema = hasJsonBody && !reqBodyJson5

        val responseBody = httpMeta?.responseBody
        val resBody = if (responseBody != null) {
            if (resBodyJson5) {
                formatAsJson5(responseBody)
            } else {
                jsonSchemaBuilder.build(responseBody)
            }
        } else {
            null
        }

        // res_body_is_json_schema: true when we use JSON schema (not JSON5) for response body
        val resBodyIsJsonSchema = responseBody != null && !resBodyJson5

        return YapiApiDoc(
            title = endpoint.name ?: "${httpMeta?.method?.name ?: "UNKNOWN"} ${endpoint.path}",
            path = formatPath(endpoint.path),
            method = httpMeta?.method?.name?.lowercase() ?: "get",
            desc = endpoint.description,
            status = endpoint.status ?: "done",
            tag = endpoint.tags,
            reqHeaders = headers.ifEmpty { null },
            reqQuery = query.ifEmpty { null },
            reqParams = pathParams.ifEmpty { null },
            reqBodyForm = formParams.ifEmpty { null },
            reqBodyOther = reqBodyOther,
            reqBodyType = reqBodyType,
            reqBodyIsJsonSchema = reqBodyIsJsonSchema,
            resBody = resBody,
            resBodyType = if (responseBody != null) "json" else null,
            resBodyIsJsonSchema = resBodyIsJsonSchema,
            tags = endpoint.tags.ifEmpty { null },
            open = if (endpoint.open) true else null
        )
    }

    /**
     * Formats an API endpoint with multiple alternative URLs.
     * Returns a separate YAPI document for each alternative path.
     * 
     * @param endpoint The API endpoint with potential alternative paths
     * @return List of YAPI documents, one for each URL variant
     */
    fun formatWithUrls(endpoint: ApiEndpoint): List<YapiApiDoc> {
        val baseDoc = format(endpoint)

        val alternativePaths = endpoint.httpMetadata?.alternativePaths
        if (alternativePaths.isNullOrEmpty()) {
            return listOf(baseDoc)
        }

        return alternativePaths.map { url ->
            baseDoc.copy(path = formatPath(url))
        }
    }

    /**
     * Formats an API endpoint with generated mock data for examples.
     * Uses the MockDataGenerator to fill in missing example values.
     * 
     * @param endpoint The API endpoint to format
     * @return A YAPI document with mock examples
     */
    fun formatWithMock(endpoint: ApiEndpoint): YapiApiDoc {
        val doc = format(endpoint)
        val parameters = endpoint.httpMetadata?.parameters ?: emptyList()

        mockGenerator?.let { generator ->
            val pathParamMap = parameters
                .filter { it.binding == ParameterBinding.Path }
                .associateBy { it.name }

            val updatedPathParams = doc.reqParams?.map { param ->
                val apiParam = pathParamMap[param.name]
                param.copy(
                    example = param.example
                        ?: apiParam?.let { generator.mockFor(it) }
                        ?: generator.mockForParam(param.name))
            }

            val queryParamMap = parameters
                .filter { it.binding == ParameterBinding.Query }
                .associateBy { it.name }

            val updatedQuery = doc.reqQuery?.map { query ->
                val apiParam = queryParamMap[query.name]
                query.copy(
                    example = query.example
                        ?: apiParam?.let { generator.mockFor(it) }
                        ?: generator.mockForQuery(query.name))
            }

            return doc.copy(
                reqParams = updatedPathParams,
                reqQuery = updatedQuery
            )
        }

        return doc
    }

    /**
     * Formats a path for YAPI compatibility.
     * Ensures the path starts with `/` and only contains allowed characters.
     * Invalid characters are replaced with `/`, and redundant slashes are collapsed.
     *
     * YAPI path requirements:
     * - Must start with `/`
     * - Only allows: letters, digits, `-`, `/`, `_`, `:`, `.`, `{`, `}`, `?`, `=`, `!`
     *
     * @param path The raw API path
     * @return A sanitized path safe for YAPI
     */
    private fun formatPath(path: String?): String {
        if (!autoFormatUrl) {
            return path ?: ""
        }
        return when {
            path.isNullOrEmpty() -> "/"
            path.startsWith("/") -> path
            else -> "/$path"
        }.let {
            REGEX_URL_CONSIST.replace(it, "/")
        }.let {
            REGEX_URL_REDUNDANT_SLASH.replace(it, "/")
        }
    }

    /**
     * Formats an object model as JSON5 string.
     * JSON5 is a more readable JSON format with comments and trailing commas.
     * 
     * @param model The object model to format
     * @return A JSON5 formatted string
     */
    private fun formatAsJson5(model: ObjectModel): String {
        return ObjectModelJsonConverter.toJson5(model)
    }

    companion object {
        /** Matches any character not allowed in YAPI paths */
        val REGEX_URL_CONSIST = Regex("[^a-zA-Z0-9-/_:.{}?=!]")

        /** Matches consecutive slashes */
        val REGEX_URL_REDUNDANT_SLASH = Regex("//+")
    }
}
