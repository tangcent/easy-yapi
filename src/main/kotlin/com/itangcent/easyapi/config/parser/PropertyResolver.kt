package com.itangcent.easyapi.config.parser

/**
 * Resolves property placeholders in configuration values.
 *
 * Handles `${key}` placeholders by looking up values from the provided
 * lookup function. Supports nested placeholders and circular reference
 * detection.
 *
 * ## Placeholder Format
 * ```
 * api.name=${project.name}-api
 * api.url=${server.host}:${server.port}
 * ```
 *
 * ## Multi-Value Resolution
 * When a key has multiple values, the resolution mode determines which
 * value to use:
 * - FIRST - Use the first value (default)
 * - LAST - Use the last value
 * - LONGEST - Use the longest value
 * - SHORTEST - Use the shortest value
 * - ERROR - Throw an error
 *
 * @param lookup Function to retrieve values for a key
 */
class PropertyResolver(
    private val lookup: (String) -> List<String>
) {
    fun resolve(
        value: String,
        resolveMulti: ResolveMultiMode = ResolveMultiMode.FIRST,
        ignoreUnresolved: Boolean = false
    ): String {
        return resolveValue(value, HashSet(), resolveMulti, ignoreUnresolved)
    }

    private fun resolveValue(
        value: String,
        resolving: MutableSet<String>,
        resolveMulti: ResolveMultiMode,
        ignoreUnresolved: Boolean
    ): String {
        val sb = StringBuilder()
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '$' && i + 1 < value.length && value[i + 1] == '{') {
                val end = value.indexOf('}', startIndex = i + 2)
                if (end > 0) {
                    val key = value.substring(i + 2, end)
                    val replacement = resolveKey(key, resolving, resolveMulti, ignoreUnresolved)
                    if (replacement != null) {
                        sb.append(replacement)
                    } else if (!ignoreUnresolved) {
                        sb.append("\${").append(key).append("}")
                    }
                    i = end + 1
                    continue
                }
            }
            sb.append(ch)
            i++
        }
        return sb.toString()
    }

    private fun resolveKey(
        key: String,
        resolving: MutableSet<String>,
        resolveMulti: ResolveMultiMode,
        ignoreUnresolved: Boolean
    ): String? {
        if (!resolving.add(key)) {
            throw IllegalStateException("Circular property reference: $key")
        }
        try {
            val values = lookup(key)
            val raw = pickValue(values, resolveMulti) ?: return null
            return resolveValue(raw, resolving, resolveMulti, ignoreUnresolved)
        } finally {
            resolving.remove(key)
        }
    }

    companion object {
        fun pickValue(values: List<String>, mode: ResolveMultiMode): String? {
            if (values.isEmpty()) return null
            if (values.size == 1) return values.first()
            return when (mode) {
                ResolveMultiMode.FIRST -> values.first()
                ResolveMultiMode.LAST -> values.last()
                ResolveMultiMode.LONGEST -> values.maxByOrNull { it.length }
                ResolveMultiMode.SHORTEST -> values.minByOrNull { it.length }
                ResolveMultiMode.ERROR -> throw IllegalArgumentException(
                    "Property has ${values.size} values but resolveMulti=ERROR"
                )
            }
        }
    }
}
