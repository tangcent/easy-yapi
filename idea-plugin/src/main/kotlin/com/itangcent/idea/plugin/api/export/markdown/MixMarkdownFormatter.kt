package com.itangcent.idea.plugin.api.export.markdown

import com.itangcent.common.model.Doc
import com.itangcent.common.model.MethodDoc
import com.jetbrains.rd.util.first
import kotlin.reflect.KClass

/**
 * A composite markdown formatter that handles different types of documentation.
 * This formatter delegates the formatting to specialized formatters based on the document type.
 *
 * @property apiMarkdownFormatter Formatter for general API documentation
 * @property methodDocMarkdownFormatter Formatter specifically for method documentation
 */
class MixMarkdownFormatter(
    private val apiMarkdownFormatter: MarkdownFormatter,
    private val methodDocMarkdownFormatter: MarkdownFormatter
) : MarkdownFormatter {
    /**
     * Parses a list of documents into markdown format.
     * Groups documents by their type and processes them using the appropriate formatter.
     *
     * @param docs List of documents to be formatted
     * @return Formatted markdown string containing all documentation
     */
    override fun parseDocs(docs: List<Doc>): String {
        val requestsMap = docs.groupBy { it::class }
        if (requestsMap.size == 1) {
            val (type, requests) = requestsMap.first()
            return parseRequests(type, requests)
        } else {
            val result = StringBuilder()
            for ((type, requests) in requestsMap) {
                result.append(parseRequests(type, requests))
            }
            return result.toString()
        }
    }

    /**
     * Parses a group of documents of the same type using the appropriate formatter.
     *
     * @param type The class type of the documents
     * @param requests List of documents to be formatted
     * @return Formatted markdown string for the group of documents
     */
    private fun parseRequests(
        type: KClass<out Doc>,
        requests: List<Doc>
    ): String {
        return if (type == MethodDoc::class) {
            methodDocMarkdownFormatter.parseDocs(requests)
        } else {
            apiMarkdownFormatter.parseDocs(requests)
        }
    }
}