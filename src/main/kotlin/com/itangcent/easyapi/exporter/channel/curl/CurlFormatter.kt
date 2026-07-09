package com.itangcent.easyapi.exporter.channel.curl

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.GrpcMetadata
import com.itangcent.easyapi.exporter.model.HttpMetadata
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
 *
 * Output formatting is controlled by [CurlFormatOptions]. The default options
 * ([CurlFormatOptions] with all defaults) produce byte-identical output to the
 * pre-options formatter, ensuring backward compatibility.
 */
object CurlFormatter {

    /**
     * Formats a single API endpoint as a cURL command (for HTTP) or grpcurl command (for gRPC).
     *
     * @param endpoint The API endpoint to format
     * @param host Optional host URL to prepend to the path
     * @param options Formatting options (default produces backward-compatible output)
     * @return A cURL or grpcurl command string
     */
    fun format(endpoint: ApiEndpoint, host: String = "", options: CurlFormatOptions = CurlFormatOptions()): String {
        return when (val meta = endpoint.metadata) {
            is HttpMetadata -> formatHttp(endpoint, meta, host, options)
            is GrpcMetadata -> formatGrpc(endpoint, meta, host, options)
        }
    }

    /**
     * Formats an HTTP endpoint as a cURL command.
     */
    private fun formatHttp(endpoint: ApiEndpoint, meta: HttpMetadata, host: String, options: CurlFormatOptions): String {
        val url = buildUrl(meta, host)
        val headers = buildHeaders(meta, options)
        val body = buildBody(endpoint, meta, options)
        val method = meta.method.name
        val parts = listOf(
            "curl",
            if (options.longFlags) "--request" else "-X",
            method,
            headers,
            body,
            "'${escapeShell(url)}'"
        ).filter { it.isNotBlank() }

        val separator = if (options.multiLineFormat) " \\\n  " else " "
        val cmd = parts.joinToString(separator)

        if (options.includeResponseExample && meta.responseBody != null) {
            val respJson = responseBodyJson(endpoint, meta, options)
            return "$cmd\n# Response: $respJson"
        }
        return cmd
    }

    /**
     * Formats a gRPC endpoint as a grpcurl command.
     *
     * Generates: grpcurl -plaintext -d '{json}' 'host:port' 'package.Service/Method'
     */
    private fun formatGrpc(endpoint: ApiEndpoint, meta: GrpcMetadata, host: String, options: CurlFormatOptions): String {
        val target = if (host.isNotBlank()) host.removePrefix("http://").removePrefix("https://") else "localhost:50051"
        val servicePath = "${meta.packageName}.${meta.serviceName}/${meta.path.substringAfterLast('/')}"
        val parts = mutableListOf("grpcurl", "-plaintext")
        meta.body?.let { body ->
            val json = ObjectModelJsonConverter.toJson(body)
            val finalJson = if (options.prettyPrintBody) prettyJson(json) else json
            parts.add("-d '${escapeShell(finalJson)}'")
        }
        parts.add("'${escapeShell(target)}'")
        parts.add("'${escapeShell(servicePath)}'")

        val separator = if (options.multiLineFormat) " \\\n  " else " "
        return parts.joinToString(separator)
    }

    /**
     * Formats multiple API endpoints as a shell script with markdown-style sections.
     * Each endpoint is separated by a divider and includes its name as a comment.
     *
     * @param endpoints The list of API endpoints to format
     * @param host Optional host URL to prepend to paths
     * @param options Formatting options (default produces backward-compatible output)
     * @return A shell script containing cURL commands for all endpoints
     */
    fun formatAll(endpoints: List<ApiEndpoint>, host: String = "", options: CurlFormatOptions = CurlFormatOptions()): String {
        if (!options.includeComments) {
            return endpoints.joinToString("\n\n") { format(it, host, options) }
        }
        return endpoints.joinToString("\n\n---\n\n") { endpoint ->
            val apiName = when (val meta = endpoint.metadata) {
                is HttpMetadata -> endpoint.name ?: "${meta.method.name}:${meta.path}"
                is GrpcMetadata -> endpoint.name ?: "gRPC:${meta.serviceName}/${meta.path.substringAfterLast('/')}"
            }
            buildString {
                append("## $apiName\n")
                append("```bash\n")
                append(format(endpoint, host, options))
                append("\n```")
            }
        }
    }

    /**
     * Builds the headers section of a cURL command.
     * Includes both endpoint-defined headers and Content-Type.
     *
     * @param meta The HTTP metadata
     * @param options Formatting options (controls `-H` vs `--header`)
     * @return A string of header flags with properly escaped values
     */
    private fun buildHeaders(meta: HttpMetadata, options: CurlFormatOptions): String {
        val flag = if (options.longFlags) "--header" else "-H"
        val headerList = mutableListOf<String>()
        meta.headers.forEach { h ->
            headerList.add("$flag '${escapeShell(h.name)}: ${escapeShell(h.value ?: "")}'")
        }
        meta.contentType?.takeIf { it.isNotBlank() }?.let { ct ->
            headerList.add("$flag 'Content-Type: ${escapeShell(ct)}'")
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
     * If the resolver stashed a resolved body JSON in [ApiEndpoint.extensions]
     * (under [EndpointVariableResolver.RESOLVED_BODY_JSON_KEY]), that JSON is used
     * as the body source (placeholders already resolved).
     *
     * @param endpoint The API endpoint (for accessing resolved body JSON in extensions)
     * @param meta The HTTP metadata
     * @param options Formatting options (controls flags and pretty-printing)
     * @return A string of -d/--data or -F/--form flags, or empty string if no body
     */
    private fun buildBody(endpoint: ApiEndpoint, meta: HttpMetadata, options: CurlFormatOptions): String {
        val contentType = meta.contentType?.lowercase().orEmpty()
        val bodyParams = meta.parameters.filter { it.binding == ParameterBinding.Body }
        val formParams = meta.parameters.filter { it.binding == ParameterBinding.Form }

        if (contentType.contains("json")) {
            val resolvedBodyJson = endpoint.extensions.exts[EndpointVariableResolver.RESOLVED_BODY_JSON_KEY] as? String
            val json = when {
                resolvedBodyJson != null -> resolvedBodyJson
                meta.body != null -> ObjectModelJsonConverter.toJson(meta.body)
                bodyParams.isEmpty() -> "{}"
                else -> bodyParams.joinToString(prefix = "{", postfix = "}") {
                    "\"${it.name}\": \"${escapeShell(it.example ?: it.defaultValue ?: "")}\""
                }
            }
            val finalJson = if (options.prettyPrintBody) prettyJson(json) else json
            val flag = if (options.longFlags) "--data" else "-d"
            return "$flag '${escapeShell(finalJson)}'"
        }
        if (contentType.contains("x-www-form-urlencoded")) {
            return formParams.joinToString(" ") {
                "--data-urlencode '${escapeShell(it.name)}=${escapeShell(it.example ?: it.defaultValue ?: "")}'"
            }
        }
        if (contentType.contains("multipart")) {
            val flag = if (options.longFlags) "--form" else "-F"
            return formParams.joinToString(" ") {
                "$flag '${escapeShell(it.name)}=${escapeShell(it.example ?: it.defaultValue ?: "")}'"
            }
        }

        if (meta.body != null) {
            val resolvedBodyJson = endpoint.extensions.exts[EndpointVariableResolver.RESOLVED_BODY_JSON_KEY] as? String
            val bodyJson = resolvedBodyJson ?: ObjectModelJsonConverter.toJson(meta.body)
            val finalJson = if (options.prettyPrintBody) prettyJson(bodyJson) else bodyJson
            val flag = if (options.longFlags) "--data" else "-d"
            return "$flag '${escapeShell(finalJson)}'"
        }

        return ""
    }

    /**
     * Computes the response body JSON for the `# Response:` comment.
     *
     * Uses the resolved body JSON from extensions if present (unlikely for response,
     * but kept for consistency), otherwise serializes [HttpMetadata.responseBody].
     * Pretty-prints if [CurlFormatOptions.prettyPrintBody] is set.
     */
    private fun responseBodyJson(endpoint: ApiEndpoint, meta: HttpMetadata, options: CurlFormatOptions): String {
        val resolved = endpoint.extensions.exts[EndpointVariableResolver.RESOLVED_BODY_JSON_KEY] as? String
        val compact = resolved ?: ObjectModelJsonConverter.toJson(meta.responseBody)
        return if (options.prettyPrintBody) prettyJson(compact) else compact
    }

    /**
     * Pretty-prints a compact JSON string. Falls back to the input on parse failure.
     */
    private fun prettyJson(compact: String): String {
        return try {
            val parsed = JsonParser.parseString(compact)
            GsonBuilder().setPrettyPrinting().create().toJson(parsed)
        } catch (t: Throwable) {
            compact
        }
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
