package com.itangcent.easyapi.core.util.text

/**
 * Shared parsing for the `key=value` / `key:value` line format used by the
 * config parser ([com.itangcent.easyapi.core.config.parser.ConfigTextParser]).
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
}
