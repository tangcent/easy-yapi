package com.itangcent.easyapi.exporter.markdown

/**
 * Utility object for escaping special characters in Markdown content.
 * 
 * Handles characters that have special meaning in Markdown tables
 * and line breaks to ensure proper rendering.
 */
object MarkdownEscapeUtils {
    /**
     * Escapes special Markdown characters in text.
     * Converts newlines to &lt;br&gt; tags and escapes pipe characters.
     * 
     * @param text The text to escape, can be null
     * @return The escaped text, or empty string if input is null/blank
     */
    fun escape(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text
            .replace("\n", "<br>")
            .replace("|", "\\|")
    }
}

