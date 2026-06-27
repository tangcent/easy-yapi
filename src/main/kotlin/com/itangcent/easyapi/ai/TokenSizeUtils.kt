package com.itangcent.easyapi.ai

/**
 * AI assistant context-window presets and token-string parsing.
 *
 * The presets back the "Context Window" combo in the settings UI
 * (see `AiAssistantSection`). Labels use plain `"8k"` shorthand so that
 * a stored value round-trips through [parse] without any preset-label
 * special-casing.
 *
 * Parsing uses **decimal** multipliers (`k` → 1_000, `m` → 1_000_000),
 * which is the convention for token counts (not byte sizes).
 *
 * UI-agnostic and Swing-free, so it can be unit-tested without the
 * IntelliJ fixture.
 */
object TokenSizeUtils {

    /**
     * Common context-window presets offered in the settings dropdown,
     * as plain shorthand strings (`"8k"`, `"1m"`, …).
     */
    val presets: List<String> = listOf(
        "8k",
        "16k",
        "32k",
        "64k",
        "128k",
        "200k",
        "500k",
        "1m",
        "2m"
    )

    /**
     * Parses a free-form context-window string into a token count using
     * **decimal** multipliers (`k` → 1_000, `m` → 1_000_000).
     *
     * Accepts (case-insensitive, surrounding whitespace ignored):
     * - `"8000"`, `"8,000"` → `8000`
     * - `"8k"`, `"8K"` → `8000`
     * - `"1m"`, `"1M"` → `1_000_000`
     * - `"8k (8,000)"` (a label) → `8000` — anything after the first
     *   whitespace is ignored
     *
     * Returns `0` for any unparseable input rather than throwing, so the
     * caller can fall back to a safe default.
     */
    fun parse(text: String): Int {
        var trimmed = text.trim().replace(",", "")
        if (trimmed.isEmpty()) return 0

        // A label like "8k (8,000)" — take the first whitespace-delimited token only.
        val firstSpace = trimmed.indexOfFirst { it.isWhitespace() }
        if (firstSpace > 0) {
            trimmed = trimmed.substring(0, firstSpace)
        }
        if (trimmed.isEmpty()) return 0

        val lower = trimmed.lowercase()
        val multiplier = when {
            lower.endsWith("k") -> 1_000
            lower.endsWith("m") -> 1_000_000
            else -> 1
        }
        val numStr = if (multiplier != 1) lower.dropLast(1) else lower
        val n = numStr.toIntOrNull() ?: return 0
        return n * multiplier
    }
}
