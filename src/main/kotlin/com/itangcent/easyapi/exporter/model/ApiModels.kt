package com.itangcent.easyapi.exporter.model

import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.SpecialTypeHandler

/**
 * Protocol-agnostic API endpoint model.
 * Contains only fields shared across all API protocols (HTTP, gRPC, etc.).
 * Protocol-specific fields are held in the [metadata] field.
 *
 * @param name The endpoint name/description
 * @param folder The folder/group for organizing endpoints
 * @param description The endpoint description
 * @param tags Tags for categorization
 * @param sourceClass The source PSI class
 * @param sourceMethod The source PSI method
 * @param className The source class name
 * @param classDescription The source class description
 * @param metadata Protocol-specific metadata (HTTP or gRPC)
 */
data class ApiEndpoint(
    val name: String? = null,
    val folder: String? = null,
    var description: String? = null,
    val tags: List<String> = emptyList(),
    val status: String? = null,
    val open: Boolean = false,
    val sourceClass: com.intellij.psi.PsiClass? = null,
    val sourceMethod: com.intellij.psi.PsiMethod? = null,
    val className: String? = null,
    val classDescription: String? = null,
    val metadata: ApiMetadata
) {
    fun setParam(name: String?, defaultValue: String?, required: Boolean, desc: String?) {
        val meta = metadata as? HttpMetadata ?: return
        meta.parameters.add(
            ApiParameter(
                name = name ?: "",
                defaultValue = defaultValue,
                required = required,
                description = desc,
                binding = ParameterBinding.Query
            )
        )
    }

    fun setFormParam(name: String?, defaultValue: String?, required: Boolean, desc: String?) {
        val meta = metadata as? HttpMetadata ?: return
        meta.parameters.add(
            ApiParameter(
                name = name ?: "",
                defaultValue = defaultValue,
                required = required,
                description = desc,
                binding = ParameterBinding.Form
            )
        )
    }

    fun setPathParam(name: String?, defaultValue: String?, desc: String?) {
        val meta = metadata as? HttpMetadata ?: return
        meta.parameters.add(
            ApiParameter(
                name = name ?: "",
                defaultValue = defaultValue,
                required = true,
                description = desc,
                binding = ParameterBinding.Path
            )
        )
    }

    fun setHeader(name: String?, defaultValue: String?, required: Boolean, desc: String?) {
        val meta = metadata as? HttpMetadata ?: return
        meta.headers.add(
            ApiHeader(
                name = name ?: "",
                value = defaultValue,
                description = desc,
                required = required
            )
        )
    }

    fun setResponseCode(code: Int) {
        // HttpMetadata doesn't have a responseCode field; kept for script compatibility
    }

    fun appendResponseBodyDesc(desc: String?) {
        // kept for script compatibility
    }

    fun setResponseHeader(name: String?, defaultValue: String?, required: Boolean, desc: String?) {
        val meta = metadata as? HttpMetadata ?: return
        meta.headers.add(
            ApiHeader(
                name = name ?: "",
                value = defaultValue,
                description = desc,
                required = required
            )
        )
    }

    fun setResponseBodyClass(className: String?) {
        val meta = metadata as? HttpMetadata ?: return
        meta.responseType = className
    }

    fun appendDesc(desc: String?) {
        if (desc == null) return
        this.description = (this.description ?: "") + desc
    }
}

/** Convenience: true if this is an HTTP endpoint */
val ApiEndpoint.isHttp: Boolean get() = metadata is HttpMetadata

/** Convenience: true if this is a gRPC endpoint */
val ApiEndpoint.isGrpc: Boolean get() = metadata is GrpcMetadata

/** Convenience accessor for HTTP metadata, or null */
val ApiEndpoint.httpMetadata: HttpMetadata? get() = metadata as? HttpMetadata

/** Convenience accessor for gRPC metadata, or null */
val ApiEndpoint.grpcMetadata: GrpcMetadata? get() = metadata as? GrpcMetadata

/** Convenience accessor for path, works for both HTTP and gRPC endpoints */
val ApiEndpoint.path: String
    get() = when (val meta = metadata) {
        is HttpMetadata -> meta.path
        is GrpcMetadata -> meta.path
    }

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
    val enumValues: List<String>? = null,
    val jsonType: String? = null
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


/**
 * Base sealed interface for protocol-specific API metadata.
 * Each supported protocol (HTTP, gRPC, etc.) provides its own implementation.
 */
sealed interface ApiMetadata {
    /** Human-readable protocol name for display (e.g., "HTTP", "gRPC") */
    val protocol: String
}

/**
 * HTTP-specific metadata for REST API endpoints.
 * Contains all fields that are HTTP-specific, extracted from the original ApiEndpoint.
 *
 * @param path The URL path
 * @param method The HTTP method
 * @param parameters The request parameters
 * @param headers The request headers
 * @param contentType The request content type
 * @param bodyAttr The request body attribute name
 * @param alternativePaths Alternative URL paths
 * @param body The request body model
 * @param responseBody The response body model
 * @param responseType The response type name
 */
data class HttpMetadata(
    var path: String,
    var method: HttpMethod,
    val parameters: MutableList<ApiParameter> = mutableListOf(),
    val headers: MutableList<ApiHeader> = mutableListOf(),
    var contentType: String? = null,
    val bodyAttr: String? = null,
    var alternativePaths: MutableList<String>? = null,
    var body: ObjectModel? = null,
    var responseBody: ObjectModel? = null,
    var responseType: String? = null
) : ApiMetadata {
    override val protocol: String = "HTTP"
}

fun httpMetadata(
    path: String,
    method: HttpMethod,
    parameters: List<ApiParameter> = emptyList(),
    headers: List<ApiHeader> = emptyList(),
    contentType: String? = null,
    bodyAttr: String? = null,
    alternativePaths: List<String>? = null,
    body: ObjectModel? = null,
    responseBody: ObjectModel? = null,
    responseType: String? = null
): HttpMetadata = HttpMetadata(
    path,
    method,
    parameters.toMutableList(),
    headers.toMutableList(),
    contentType,
    bodyAttr,
    alternativePaths = alternativePaths?.toMutableList(),
    body,
    responseBody,
    responseType
)

/**
 * gRPC-specific metadata for gRPC service endpoints.
 *
 * @param path The gRPC service path (e.g., /package.ServiceName/MethodName)
 * @param serviceName The gRPC service name
 * @param methodName The gRPC method name
 * @param packageName The protobuf package name
 * @param streamingType The gRPC communication pattern
 * @param protoFile The source .proto file path (optional)
 * @param body The request body model
 * @param responseBody The response body model
 * @param responseType The response type name
 */
data class GrpcMetadata(
    val path: String,
    val serviceName: String,
    val methodName: String,
    val packageName: String,
    val streamingType: GrpcStreamingType,
    val protoFile: String? = null,
    val body: ObjectModel? = null,
    val responseBody: ObjectModel? = null,
    val responseType: String? = null
) : ApiMetadata {
    override val protocol: String = "gRPC"
}

/**
 * The four gRPC communication patterns.
 */
enum class GrpcStreamingType {
    /** Single request, single response */
    UNARY,

    /** Single request, stream of responses */
    SERVER_STREAMING,

    /** Stream of requests, single response */
    CLIENT_STREAMING,

    /** Stream of requests, stream of responses */
    BIDIRECTIONAL
}
