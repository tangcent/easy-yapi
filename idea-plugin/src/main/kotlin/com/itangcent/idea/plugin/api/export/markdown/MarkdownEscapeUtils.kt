package com.itangcent.idea.plugin.api.export.markdown

/**
 * Utility class for escaping markdown special characters in tables
 */
object MarkdownEscapeUtils {
    /**
     * Escapes special markdown characters in table cells
     * @param text The text to escape
     * @return The escaped text
     */
    fun escape(text: String?): String {
        if (text.isNullOrBlank()) return ""

        return text.replace("\n", "<br>")
            .replace("|", "\\|")
    }
} 