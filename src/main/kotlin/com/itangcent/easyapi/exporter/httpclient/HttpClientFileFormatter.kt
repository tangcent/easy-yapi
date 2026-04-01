package com.itangcent.easyapi.exporter.httpclient

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ParameterBinding

/**
 * Formatter for generating IntelliJ HTTP Client file format.
 * 
 * This is a simpler implementation that generates compact HTTP Client
 * format output. Each endpoint is separated by a "###" delimiter.
 * 
 * Output format is compatible with IntelliJ IDEA's HTTP Client.
 */
object HttpClientFileFormatter {
    /**
     * Formats a list of API endpoints as an HTTP Client file.
     * Generates compact format with method, URL, headers, and body.
     * 
     * @param endpoints The list of API endpoints to format
     * @param host The host URL to prepend to paths
     * @return A complete HTTP Client file content
     */
    fun format(endpoints: List<ApiEndpoint>, host: String): String {
        val sb = StringBuilder()
        for ((idx, ep) in endpoints.withIndex()) {
            sb.append(ep.method.name).append(' ').append(host.trimEnd('/')).append(ep.path)
            val query = ep.parameters.filter { it.binding == ParameterBinding.Query }
            if (query.isNotEmpty()) {
                sb.append('?').append(query.joinToString("&") { "${it.name}=${it.example ?: it.defaultValue ?: ""}" })
            }
            sb.append('\n')
            for (h in ep.headers) {
                sb.append(h.name).append(": ").append(h.value ?: "").append('\n')
            }
            val contentType = ep.contentType
            if (!contentType.isNullOrBlank()) {
                sb.append("Content-Type: ").append(contentType).append('\n')
            }
            val bodyParams = ep.parameters.filter { it.binding == ParameterBinding.Body }
            val formParams = ep.parameters.filter { it.binding == ParameterBinding.Form }
            if (bodyParams.isNotEmpty() || formParams.isNotEmpty()) {
                sb.append('\n')
            }
            if (bodyParams.isNotEmpty()) {
                sb.append(bodyParams.joinToString(prefix = "{\n", postfix = "\n}\n") { "  \"${it.name}\": \"${it.example ?: it.defaultValue ?: ""}\"" })
            } else if (formParams.isNotEmpty()) {
                sb.append(formParams.joinToString("&") { "${it.name}=${it.example ?: it.defaultValue ?: ""}" }).append('\n')
            }
            if (idx != endpoints.lastIndex) {
                sb.append("\n###\n\n")
            }
        }
        return sb.toString()
    }
}

