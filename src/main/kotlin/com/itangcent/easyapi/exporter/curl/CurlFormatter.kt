package com.itangcent.easyapi.exporter.curl

import com.itangcent.easyapi.exporter.model.ApiEndpoint
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
     * Formats a single API endpoint as a cURL command.
     * 
     * @param endpoint The API endpoint to format
     * @param host Optional host URL to prepend to the path
     * @return A cURL command string
     */
    fun format(endpoint: ApiEndpoint, host: String = ""): String {
        val url = buildUrl(endpoint, host)
        val headers = buildHeaders(endpoint)
        val body = buildBody(endpoint)
        val method = endpoint.method.name
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
     * Formats multiple API endpoints as a shell script with markdown-style sections.
     * Each endpoint is separated by a divider and includes its name as a comment.
     * 
     * @param endpoints The list of API endpoints to format
     * @param host Optional host URL to prepend to paths
     * @return A shell script containing cURL commands for all endpoints
     */
    fun formatAll(endpoints: List<ApiEndpoint>, host: String = ""): String {
        return endpoints.joinToString("\n\n---\n\n") { endpoint ->
            val apiName = endpoint.name ?: "${endpoint.method.name}:${endpoint.path}"
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
     * @param endpoint The API endpoint
     * @return A string of -H flags with properly escaped values
     */
    private fun buildHeaders(endpoint: ApiEndpoint): String {
        val headerList = mutableListOf<String>()
        endpoint.headers.forEach { h ->
            headerList.add("-H '${escapeShell(h.name)}: ${escapeShell(h.value ?: "")}'")
        }
        endpoint.contentType?.takeIf { it.isNotBlank() }?.let { ct ->
            headerList.add("-H 'Content-Type: ${escapeShell(ct)}'")
        }
        return headerList.joinToString(" ")
    }

    /**
     * Builds the complete URL with query parameters.
     * 
     * @param endpoint The API endpoint
     * @param host The host URL to prepend
     * @return The complete URL with query string if applicable
     */
    private fun buildUrl(endpoint: ApiEndpoint, host: String): String {
        val query = endpoint.parameters.filter { it.binding == ParameterBinding.Query }
        val base = if (host.isNotBlank()) host.trimEnd('/') + endpoint.path else endpoint.path
        if (query.isEmpty()) return base
        return base + "?" + query.joinToString("&") { "${it.name}=${it.example ?: it.defaultValue ?: ""}" }
    }

    /**
     * Builds the body/data section of a cURL command.
     * Handles different content types: JSON, form-urlencoded, and multipart.
     * 
     * @param endpoint The API endpoint
     * @return A string of -d or -F flags, or empty string if no body
     */
    private fun buildBody(endpoint: ApiEndpoint): String {
        val contentType = endpoint.contentType?.lowercase().orEmpty()
        val bodyParams = endpoint.parameters.filter { it.binding == ParameterBinding.Body }
        val formParams = endpoint.parameters.filter { it.binding == ParameterBinding.Form }

        if (contentType.contains("json")) {
            val json = when {
                endpoint.body != null -> ObjectModelJsonConverter.toJson(endpoint.body)
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

        if (endpoint.body != null) {
            val bodyJson = ObjectModelJsonConverter.toJson(endpoint.body)
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
