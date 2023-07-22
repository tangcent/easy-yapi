package com.itangcent.utils

/**
 * A map of escape characters to their unescaped versions.
 */
private val escapeCharacters = mapOf(
    'b' to '\b',   // Backspace
    't' to '\t',   // Tab
    'n' to '\n',   // Newline
    'r' to '\r',   // Carriage return
    '\\' to '\\',  // Backslash
)

/**
 * Used to convert escaped characters in a string to their unescaped version.
 * For example, "\\n" would be converted to "\n".
 *
 * @return The unescaped string.
 */
fun String.unescape(): String {
    val sb = StringBuilder()
    var escaped = false
    for (ch in this) {
        if (!escaped) {
            if (ch == '\\') {
                escaped = true
            } else {
                sb.append(ch)
            }
            continue
        }

        escaped = false
        if (escapeCharacters.containsKey(ch)) {
            sb.append(escapeCharacters[ch])
            continue
        }

        sb.append('\\')
        sb.append(ch)
        continue
    }
    if (escaped) {
        sb.append('\\')
    }
    return sb.toString()
}