package com.itangcent.easyapi.exporter.curl

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.GrpcMetadata
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.psi.model.ObjectModelJsonConverter

/**
 * Utility object for formatting API endpoints as cURL commands.
 * 
 * This formatter generates shell-compatible cURL commands with:
 * - Proper HTTP method specification
 * - Header inclusion
 * - Body content handling (JSON, form-urlencoded, multipart)
 * - Shell-safe escaping of special characters
 */
object CurlFormatter {

    /**
     * Formats a single API endpoint as a cURL command (for HTTP) or grpcurl command (for gRPC).
     * 
     * @param endpoint The API endpoint to format
     * @param host Optional host URL to prepend to the path
     * @return A cURL or grpcurl command string
     */
    fun format(endpoint: ApiEndpoint, host: String = ""): String {
        return when (val meta = endpoint.metadata) {
            is HttpMetadata -> formatHttp(endpoint, meta, host)
            is GrpcMetadata -> formatGrpc(endpoint, meta, host)
        }
    }

    /**
     * Formats an HTTP endpoint as a cURL command.
     */
    private fun formatHttp(endpoint: ApiEndpoint, meta: HttpMetadata, host: String): String {
        val url = buildUrl(meta, host)
        val headers = buildHeaders(meta)
        val body = buildBody(meta)
        val method = meta.method.name
        return listOf(
            "curl",
            "-X",
            method,
            headers,
            body,
            "'${escapeShell(url)}'"
        ).filter { it.isNotBlank() }.joinToString(" ")
    }

    /**
     * Formats a gRPC endpoint as a grpcurl command.
     *
     * Generates: grpcurl -plaintext -d '{json}' 'host:port' 'package.Service/Method'
     */
    private fun formatGrpc(endpoint: ApiEndpoint, meta: GrpcMetadata, host: String): String {
        val target = if (host.isNotBlank()) host.removePrefix("http://").removePrefix("https://") else "localhost:50051"
        val servicePath = "${meta.packageName}.${meta.serviceName}/${meta.path.substringAfterLast('/')}"
        val parts = mutableListOf("grpcurl", "-plaintext")
        meta.body?.let { body ->
            parts.add("-d '${escapeShell(ObjectModelJsonConverter.toJson(body))}'")
        }
        parts.add("'${escapeShell(target)}'")
        parts.add("'${escapeShell(servicePath)}'")
        return parts.joinToString(" ")
    }

    /**
     * Formats multiple API endpoints as a shell script with markdown-style sections.
     * Each endpoint is separated by a divider and includes its name as a comment.
     * 
     * @param endpoints The list of API endpoints to format
     * @param host Optional host URL to prepend to paths
     * @return A shell script containing cURL commands for all endpoints
     */
    fun formatAll(endpoints: List<ApiEndpoint>, host: String = ""): String {
        return endpoints.joinToString("\n\n---\n\n") { endpoint ->
            val apiName = when (val meta = endpoint.metadata) {
                is HttpMetadata -> endpoint.name ?: "${meta.method.name}:${meta.path}"
                is GrpcMetadata -> endpoint.name ?: "gRPC:${meta.serviceName}/${meta.path.substringAfterLast('/')}"
            }
            buildString {
                append("## $apiName\n")
                append("```bash\n")
                append(format(endpoint, host))
                append("\n```")
            }
        }
    }

    /**
     * Builds the headers section of a cURL command.
     * Includes both endpoint-defined headers and Content-Type.
     * 
     * @param meta The HTTP metadata
     * @return A string of -H flags with properly escaped values
     */
    private fun buildHeaders(meta: HttpMetadata): String {
        val headerList = mutableListOf<String>()
        meta.headers.forEach { h ->
            headerList.add("-H '${escapeShell(h.name)}: ${escapeShell(h.value ?: "")}'")
        }
        meta.contentType?.takeIf { it.isNotBlank() }?.let { ct ->
            headerList.add("-H 'Content-Type: ${escapeShell(ct)}'")
        }
        return headerList.joinToString(" ")
    }

    /**
     * Builds the complete URL with query parameters.
     * 
     * @param meta The HTTP metadata
     * @param host The host URL to prepend
     * @return The complete URL with query string if applicable
     */
    private fun buildUrl(meta: HttpMetadata, host: String): String {
        val query = meta.parameters.filter { it.binding == ParameterBinding.Query }
        val base = if (host.isNotBlank()) host.trimEnd('/') + meta.path else meta.path
        if (query.isEmpty()) return base
        return base + "?" + query.joinToString("&") { "${it.name}=${it.example ?: it.defaultValue ?: ""}" }
    }

    /**
     * Builds the body/data section of a cURL command.
     * Handles different content types: JSON, form-urlencoded, and multipart.
     * 
     * @param meta The HTTP metadata
     * @return A string of -d or -F flags, or empty string if no body
     */
    private fun buildBody(meta: HttpMetadata): String {
        val contentType = meta.contentType?.lowercase().orEmpty()
        val bodyParams = meta.parameters.filter { it.binding == ParameterBinding.Body }
        val formParams = meta.parameters.filter { it.binding == ParameterBinding.Form }

        if (contentType.contains("json")) {
            val json = when {
                meta.body != null -> ObjectModelJsonConverter.toJson(meta.body)
                bodyParams.isEmpty() -> "{}"
                else -> bodyParams.joinToString(prefix = "{", postfix = "}") { 
                    "\"${it.name}\": \"${escapeShell(it.example ?: it.defaultValue ?: "")}\"" 
                }
            }
            return "-d '${escapeShell(json)}'"
        }
        if (contentType.contains("x-www-form-urlencoded")) {
            return formParams.joinToString(" ") { 
                "--data-urlencode '${escapeShell(it.name)}=${escapeShell(it.example ?: it.defaultValue ?: "")}'" 
            }
        }
        if (contentType.contains("multipart")) {
            return formParams.joinToString(" ") { 
                "-F '${escapeShell(it.name)}=${escapeShell(it.example ?: it.defaultValue ?: "")}'" 
            }
        }

        if (meta.body != null) {
            val bodyJson = ObjectModelJsonConverter.toJson(meta.body)
            return "-d '${escapeShell(bodyJson)}'"
        }

        return ""
    }

    /**
     * Escapes special characters for safe shell usage.
     * Replaces backslashes and single quotes with their escaped equivalents.
     * 
     * @param value The string to escape
     * @return The shell-safe string
     */
    fun escapeShell(value: String): String {
        return value.replace("\\", "\\\\").replace("'", "'\\''")
    }
}
