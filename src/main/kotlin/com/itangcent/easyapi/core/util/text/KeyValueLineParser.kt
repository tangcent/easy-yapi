package com.itangcent.easyapi.core.util.text

/**
 * Shared parsing for the `key[filter]=value` / `key:value` line format used by
 * both the config parser ([com.itangcent.easyapi.core.config.parser.ConfigTextParser])
 * and the rule proposal validator
 * ([com.itangcent.easyapi.core.rule.RuleProposalValidator]).
 *
 * The split honors bracket depth so that `=` / `:` appearing inside a `[...]`
 * filter (or anywhere in the value) does not trip the separator detection.
 */
object KeyValueLineParser {

    /**
     * Split [line] at the first top-level `=` / `:` separator, ignoring any
     * separator nested inside `[...]`.
     *
     * @return the trimmed left-hand side (key, possibly including a `[filter]`)
     *   paired with the trimmed value, or `null` when there is no valid
     *   separator or the left-hand side is empty.
     */
    fun splitKeyValue(line: String): Pair<String, String>? {
        var bracketDepth = 0
        var idx = -1
        for (i in line.indices) {
            when (line[i]) {
                '[' -> bracketDepth++
                ']' -> if (bracketDepth > 0) bracketDepth--
                '=', ':' -> if (bracketDepth == 0 && i > 0) {
                    idx = i
                    break
                }
            }
        }
        if (idx <= 0) return null
        val key = line.substring(0, idx).trim()
        val value = line.substring(idx + 1).trim()
        return if (key.isEmpty()) null else key to value
    }

    /**
     * Like [splitKeyValue], but further decomposes the left-hand side into a
     * `key` and an optional `[filter]`.
     *
     * When the left-hand side is not of the form `key[filter]` (no brackets, or
     * not terminated by `]`, or an empty key/filter), the whole left-hand side
     * is returned as the key with a `null` filter.
     *
     * @return a triple of `(key, filter, value)`, or `null` when [line] has no
     *   valid separator.
     */
    fun splitKeyFilterValue(line: String): Triple<String, String?, String>? {
        val (left, value) = splitKeyValue(line) ?: return null
        val bracketStart = left.indexOf('[')
        if (bracketStart < 0 || !left.endsWith("]")) return Triple(left, null, value)
        val key = left.substring(0, bracketStart).trim()
        val filter = left.substring(bracketStart + 1, left.length - 1).trim()
        if (key.isEmpty() || filter.isEmpty()) return Triple(left, null, value)
        return Triple(key, filter, value)
    }
}
