package com.itangcent.easyapi.core.http

/**
 * Pure classification and decoding helpers for response bodies.
 *
 * Splits a response into a text carrier vs a binary carrier based on its content type.
 * This is deliberately separate from the Dashboard's `formatCategoryOf` (which classifies
 * a body for *formatting*, falling back to JSON for unknown types): here unknown types
 * fall back to *binary*, so an unrecognised media type is treated as raw bytes rather than
 * being corrupted by UTF-8 decoding.
 */
object ResponseBodyClassifier {

    /**
     * Whether [contentType] denotes a text response that should be decoded to a [String].
     *
     * Whitespace and parameters (e.g. `; charset=utf-8`) are ignored. `null` and blank
     * values are treated as non-text (binary), which is the safe default.
     */
    fun isTextContentType(contentType: String?): Boolean {
        val ct = contentType?.substringBefore(';')?.trim()?.lowercase() ?: return false
        if (ct.isEmpty()) return false
        if (ct.startsWith("text/")) return true
        return when (ct) {
            "application/json",
            "application/xml",
            "application/javascript",
            "application/x-javascript",
            "application/ecmascript",
            "application/x-www-form-urlencoded",
            "application/graphql",
            "application/x-www-form-urlencoded",
            "application/yaml",
            "application/x-yaml" -> true

            else -> ct.endsWith("+json") || ct.endsWith("+xml")
        }
    }

    /**
     * Extracts a suggested file name from a `Content-Disposition` header value, or null
     * when none can be parsed. Handles both `filename="..."` and RFC 5987 `filename*=UTF-8''...`
     * (the latter is percent-decoded).
     */
    fun extractFilename(contentDisposition: String?): String? {
        if (contentDisposition.isNullOrBlank()) return null
        val filenameStar = Regex("filename\\*\\s*=\\s*UTF-8''([^;]+)", RegexOption.IGNORE_CASE)
            .find(contentDisposition)?.groupValues?.get(1)
        if (!filenameStar.isNullOrBlank()) {
            return decodePercent(filenameStar.trim())
        }
        val filename = Regex("filename\\s*=\\s*\"([^\"]+)\"|filename\\s*=\\s*([^;]+)", RegexOption.IGNORE_CASE)
            .find(contentDisposition)?.groupValues?.let { it.getOrNull(1)?.ifBlank { it.getOrNull(2) } }
        return filename?.trim()?.trim('"')?.ifBlank { null }
    }

    private fun decodePercent(value: String): String =
        java.net.URLDecoder.decode(value, Charsets.UTF_8.name())
}
