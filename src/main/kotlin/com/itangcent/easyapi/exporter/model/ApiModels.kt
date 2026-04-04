package com.itangcent.easyapi.exporter.model

import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.SpecialTypeHandler

/**
 * Represents a single API endpoint extracted from source code.
 *
 * Contains all information needed to document or export an API:
 * - HTTP method and path
 * - Parameters and headers
 * - Request/response body models
 * - Source code references
 *
 * @param name The endpoint name/description
 * @param folder The folder/group for organizing endpoints
 * @param path The URL path (e.g., "/api/users/{id}")
 * @param method The HTTP method
 * @param parameters The request parameters
 * @param headers The request headers
 * @param contentType The request content type
 * @param description The endpoint description
 * @param responseType The response type name
 * @param tags Tags for categorization
 * @param sourceClass The source PSI class
 * @param sourceMethod The source PSI method
 * @param className The source class name
 * @param classDescription The source class description
 * @param body The request body model
 * @param bodyAttr The request body attribute name
 * @param alternativePaths Alternative URL paths
 * @param responseBody The response body model
 * @param status The endpoint status
 */
data class ApiEndpoint(
    val name: String? = null,
    val folder: String? = null,
    val path: String,
    val method: HttpMethod,
    val parameters: List<ApiParameter> = emptyList(),
    val headers: List<ApiHeader> = emptyList(),
    val contentType: String? = null,
    val description: String? = null,
    val responseType: String? = null,
    val tags: List<String> = emptyList(),
    val sourceClass: com.intellij.psi.PsiClass? = null,
    val sourceMethod: com.intellij.psi.PsiMethod? = null,
    val className: String? = null,
    val classDescription: String? = null,
    val body: ObjectModel? = null,
    val bodyAttr: String? = null,
    val alternativePaths: List<String>? = null,
    val responseBody: ObjectModel? = null,
    val status: String? = null
)

/**
 * The wire-level type of an HTTP parameter.
 *
 * Only two values matter at the HTTP layer:
 * - [TEXT] — a plain scalar/string value sent as form field or query string
 * - [FILE] — a binary file upload (single or multiple)
 */
enum class ParameterType {
    TEXT,
    FILE;

    fun rawType(): String = name.lowercase()

    companion object {
        /**
         * Resolves a [ParameterType] from a raw Java class name or JSON type string.
         *
         * Returns [FILE] for any name that represents a file upload type
         * (e.g. `"file"`, `"file[]"`, `"MultipartFile"`, `"Part"`, fully-qualified variants).
         * Returns [TEXT] for everything else, including `null`.
         */
        fun fromTypeName(typeName: String?): ParameterType {
            if (typeName.isNullOrBlank()) return TEXT
            return if (SpecialTypeHandler.isFileTypeName(typeName)) FILE else TEXT
        }
    }
}

/**
 * Represents a parameter for an API endpoint.
 *
 * @param name The parameter name
 * @param type The wire-level parameter type (text or file). Defaults to [ParameterType.TEXT].
 * @param required Whether the parameter is required
 * @param binding The parameter binding location (query, path, etc.)
 * @param defaultValue The default value
 * @param description The parameter description
 * @param example An example value
 * @param enumValues Possible enum values
 */
data class ApiParameter(
    val name: String,
    val type: ParameterType = ParameterType.TEXT,
    val required: Boolean = false,
    val binding: ParameterBinding? = null,
    val defaultValue: String? = null,
    val description: String? = null,
    val example: String? = null,
    val enumValues: List<String>? = null
)

/**
 * Represents an HTTP header for an API request.
 *
 * @param name The header name
 * @param value The header value
 * @param description The header description
 * @param example An example value
 * @param required Whether the header is required
 */
data class ApiHeader(
    val name: String,
    val value: String? = null,
    val description: String? = null,
    val example: String? = null,
    val required: Boolean = false
)

/**
 * HTTP methods supported by the API system.
 */
enum class HttpMethod {
    GET,
    POST,
    PUT,
    DELETE,
    PATCH,
    HEAD,
    OPTIONS,
    NO_METHOD;

    companion object {
        /**
         * Converts a Spring annotation method name to HttpMethod.
         */
        fun fromSpring(method: String): HttpMethod? {
            return when (method.uppercase()) {
                "GET" -> GET
                "POST" -> POST
                "PUT" -> PUT
                "DELETE" -> DELETE
                "PATCH" -> PATCH
                "HEAD" -> HEAD
                "OPTIONS" -> OPTIONS
                else -> null
            }
        }
    }
}

/**
 * Represents where a parameter is bound in an HTTP request.
 *
 * Sealed class hierarchy for type-safe parameter binding specification.
 */
sealed class ParameterBinding {
    /** Query string parameter (?name=value) */
    data object Query : ParameterBinding()

    /** URL path parameter (/users/{id}) */
    data object Path : ParameterBinding()

    /** HTTP header */
    data object Header : ParameterBinding()

    /** Cookie value */
    data object Cookie : ParameterBinding()

    /** Request body */
    data object Body : ParameterBinding()

    /** Form data */
    data object Form : ParameterBinding()

    /** Framework-injected parameter that should be ignored (e.g. HttpServletRequest, HttpServletResponse) */
    data object Ignored : ParameterBinding()
}
