package com.itangcent.easyapi.exporter.markdown

import com.itangcent.easyapi.exporter.model.ApiEndpoint

/**
 * Interface for formatting API endpoints as Markdown documentation.
 * 
 * Implementations can provide different Markdown output styles
 * and levels of detail.
 */
interface MarkdownFormatter {
    /**
     * Formats a list of API endpoints as Markdown documentation.
     * 
     * @param endpoints The list of API endpoints to format
     * @param moduleName The name of the module for the document title
     * @return A Markdown-formatted string
     */
    suspend fun format(endpoints: List<ApiEndpoint>, moduleName: String): String
}

