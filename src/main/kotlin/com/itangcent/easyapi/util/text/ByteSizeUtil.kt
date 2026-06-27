package com.itangcent.easyapi.util.text

/**
 * Pure parser/formatter for human-friendly **byte** size strings using
 * **binary** multipliers (`KB` → 1024, `MB` → 1024², `GB` → 1024³).
 *
 * Accepts (case-insensitive, surrounding whitespace ignored):
 * - `"1024"`, `"1,024"` → `1024`
 * - `"1k"`, `"1K"`, `"1kb"`, `"1KB"` → `1024`
 * - `"1m"`, `"1MB"` → `1_048_576`
 * - `"1g"`, `"1GB"` → `1_073_741_824`
 * - `"1 KB"` (with space) → `1024`
 *
 * Returns `0` for any unparseable input rather than throwing, so the
 * caller can fall back to a safe default.
 *
 * Multipliers are binary (1024) — this is the convention for file/cache
 * sizes. For token counts (decimal, 1000) use
 * [com.itangcent.easyapi.ai.TokenSizeUtils].
 */
object ByteSizeUtil {

    private const val KB: Long = 1024L
    private const val MB: Long = KB * 1024
    private const val GB: Long = MB * 1024

    /**
     * Parses a free-form byte-size string into a [Long].
     *
     * Accepts plain integers (`"1024"`), comma-grouped integers
     * (`"1,024"`), shorthand with binary suffixes (`"1k"`, `"1KB"`,
     * `"1MB"`, `"1GB"`), and space-separated forms (`"1 KB"`,
     * `"1.5 KB"`). Decimal numbers are rounded to the nearest byte.
     *
     * Returns `0` for blank or unparseable input. Negative numbers are
     * passed through unclamped — callers own clamping.
     */
    fun parse(text: String): Long {
        // Strip commas and all whitespace so "1,024", "1 KB", and "1.0 KB" all work.
        val cleaned = text.trim().replace(",", "").replace(Regex("\\s+"), "")
        if (cleaned.isEmpty()) return 0L

        val lower = cleaned.lowercase()
        // Order matters: check two-char suffixes (kb/mb/gb) before single-char (k/m/g/b).
        val (multiplier, suffixLen) = when {
            lower.endsWith("gb") -> GB to 2
            lower.endsWith("mb") -> MB to 2
            lower.endsWith("kb") -> KB to 2
            lower.endsWith("g") -> GB to 1
            lower.endsWith("m") -> MB to 1
            lower.endsWith("k") -> KB to 1
            lower.endsWith("b") -> 1L to 1 // bytes — strip the unit
            else -> 1L to 0
        }
        val numStr = if (suffixLen > 0) lower.dropLast(suffixLen) else lower
        // Use double to handle formatted output like "1.5" from format().
        val n = numStr.toDoubleOrNull() ?: return 0L
        return (n * multiplier).toLong()
    }

    /**
     * Formats a byte count into a human-readable string using binary
     * multipliers (B, KB, MB, GB), keeping one decimal place for the
     * scaled units.
     *
     * Examples: `0` → `"0 B"`, `1023` → `"1023 B"`, `1024` → `"1.0 KB"`,
     * `1536` → `"1.5 KB"`, `2_097_152` → `"2.0 MB"`.
     */
    fun format(bytes: Long): String = when {
        bytes < KB -> "$bytes B"
        bytes < MB -> String.format("%.1f KB", bytes / KB.toDouble())
        bytes < GB -> String.format("%.1f MB", bytes / MB.toDouble())
        else -> String.format("%.1f GB", bytes / GB.toDouble())
    }
}
