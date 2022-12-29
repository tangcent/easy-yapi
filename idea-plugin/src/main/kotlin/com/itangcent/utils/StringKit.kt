package com.itangcent.utils


private val escapeCharacters = mapOf(
    'b' to '\b',
    't' to '\t',
    'n' to '\n',
    'r' to '\r',
    '\\' to '\\',
)

/**
 * Used to convert escaped characters to their unescaped version.
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