package com.itangcent.easyapi.exporter.feign

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.itangcent.easyapi.exporter.model.ApiHeader
import com.itangcent.easyapi.exporter.model.ApiParameter
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.psi.helper.AnnotationHelper
import com.itangcent.easyapi.psi.type.JsonType

/**
 * Parser for native Feign annotations.
 * 
 * This parser handles the parsing of Feign-specific annotations such as:
 * - @RequestLine for HTTP method and path
 * - @Headers for request headers
 * - @Body for request body templates
 * - @Param for parameter binding
 * 
 * It converts these annotations into the common API model used by the exporter.
 * 
 * @param annotationHelper Helper for accessing annotation attributes
 */
class NativeFeignAnnotationParser(
    private val annotationHelper: AnnotationHelper
) {
    /**
     * Parses the @RequestLine annotation to extract HTTP method and path.
     * 
     * @param method The PSI method to parse
     * @return A RequestLine containing method and path, or null if not annotated
     */
    suspend fun parseRequestLine(method: PsiMethod): RequestLine? {
        if (!annotationHelper.hasAnn(method, "feign.RequestLine")) return null
        val value = annotationHelper.findAttrAsString(method, "feign.RequestLine", "value") ?: return null
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split(" ", limit = 2).map { it.trim() }
        val http = parts.firstOrNull().orEmpty()
        val path = parts.getOrNull(1).orEmpty()
        val httpMethod = runCatching { HttpMethod.valueOf(http.uppercase()) }.getOrNull() ?: HttpMethod.GET
        return RequestLine(httpMethod, if (path.isBlank()) "/" else path)
    }

    /**
     * Parses the @Headers annotation to extract HTTP headers.
     * 
     * @param method The PSI method to parse
     * @return List of API headers from the annotation
     */
    suspend fun parseHeaders(method: PsiMethod): List<ApiHeader> {
        if (!annotationHelper.hasAnn(method, "feign.Headers")) return emptyList()
        val map = annotationHelper.findAnnMap(method, "feign.Headers").orEmpty()
        val raw = map["value"]
        val values = when (raw) {
            is String -> listOf(raw)
            is List<*> -> raw.filterIsInstance<String>()
            else -> emptyList()
        }
        return values.mapNotNull { parseHeader(it) }
    }

    /**
     * Parses the @Body annotation to extract the body template.
     * 
     * @param method The PSI method to parse
     * @return The body template string, or null if not annotated
     */
    suspend fun parseBodyTemplate(method: PsiMethod): String? {
        if (!annotationHelper.hasAnn(method, "feign.Body")) return null
        return annotationHelper.findAttrAsString(method, "feign.Body", "value")
    }

    /**
     * Parses the @Param annotation to get the parameter binding name.
     * 
     * @param parameter The PSI parameter to parse
     * @return The parameter name for template binding, or null if not annotated
     */
    suspend fun bindParam(parameter: PsiParameter): String? {
        if (!annotationHelper.hasAnn(parameter, "feign.Param")) return null
        return annotationHelper.findAttrAsString(parameter, "feign.Param", "value")?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Extracts template variables from a string (e.g., {id}, {name}).
     * 
     * @param template The template string to parse
     * @return List of variable names found in the template
     */
    fun extractTemplateVariables(template: String): List<String> {
        return Regex("\\{([^}]+)\\}").findAll(template).map { it.groupValues[1] }.distinct().toList()
    }

    /**
     * Converts path template variables to API path parameters.
     * Matches template variables with method parameters annotated with @Param.
     * 
     * @param pathTemplate The path template containing variables
     * @param method The PSI method containing parameter definitions
     * @return List of API parameters for path variables
     */
    suspend fun toPathParams(pathTemplate: String, method: PsiMethod): List<ApiParameter> {
        val vars = extractTemplateVariables(pathTemplate)
        if (vars.isEmpty()) return emptyList()
        val result = ArrayList<ApiParameter>()
        for (v in vars) {
            val param = method.parameterList.parameters.firstOrNull { runCatching { bindParam(it) == v }.getOrNull() == true }
            result.add(
                ApiParameter(
                    name = v,
                    type = param?.type?.canonicalText,
                    binding = ParameterBinding.Path
                )
            )
        }
        return result
    }

    /**
     * Resolves header templates to concrete headers and identifies header variables.
     * 
     * @param headers The list of headers with potential template variables
     * @param method The PSI method containing parameter definitions
     * @return Pair of resolved headers and set of variable names used in headers
     */
    suspend fun toHeaderParams(headers: List<ApiHeader>, method: PsiMethod): Pair<List<ApiHeader>, Set<String>> {
        val headerVars = mutableSetOf<String>()
        val resolvedHeaders = headers.map { header ->
            val templateVars = extractTemplateVariables(header.value ?: "")
            templateVars.forEach { headerVars.add(it) }
            val value = templateVars.fold(header.value ?: "") { acc, v ->
                val param = method.parameterList.parameters.firstOrNull { runCatching { bindParam(it) == v }.getOrNull() == true }
                acc.replace("{$v}", param?.type?.canonicalText ?: "{$v}")
            }
            header.copy(value = value)
        }
        return Pair(resolvedHeaders, headerVars)
    }

    /**
     * Converts method parameters to query parameters.
     * Excludes parameters that are already bound to path or header variables.
     * 
     * @param method The PSI method containing parameter definitions
     * @param reserved Set of parameter names already used for path/header
     * @return List of API parameters for query string
     */
    suspend fun toQueryParams(method: PsiMethod, reserved: Set<String>): List<ApiParameter> {
        val result = ArrayList<ApiParameter>()
        for (p in method.parameterList.parameters) {
            if (reserved.contains(bindParam(p))) continue
            result.add(
                ApiParameter(
                    name = p.name ?: continue,
                    type = JsonType.fromPsiType(p.type),
                    binding = ParameterBinding.Query
                )
            )
        }
        return result
    }

    /**
     * Parses a header string in "Name: Value" format.
     * 
     * @param header The header string to parse
     * @return An ApiHeader, or null if parsing fails
     */
    private fun parseHeader(header: String): ApiHeader? {
        val parts = header.split(":", limit = 2).map { it.trim() }
        val name = parts.firstOrNull() ?: return null
        val value = parts.getOrNull(1)
        return ApiHeader(name, value)
    }
}

/**
 * Data class representing a parsed Feign request line.
 * 
 * @property method The HTTP method (GET, POST, etc.)
 * @property path The request path template
 */
data class RequestLine(
    val method: HttpMethod,
    val path: String
)