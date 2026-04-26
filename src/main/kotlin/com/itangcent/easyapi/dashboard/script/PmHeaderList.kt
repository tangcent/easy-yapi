package com.itangcent.easyapi.dashboard.script

/**
 * A case-insensitive mutable list of HTTP headers, compatible with Postman's header API.
 *
 * Header names are compared case-insensitively for lookup and removal operations,
 * but the original case is preserved when iterating.
 *
 * Groovy usage:
 * ```groovy
 * pm.request.headers.add("Content-Type", "application/json")
 * pm.request.headers.upsert("Authorization", "Bearer token")
 * pm.request.headers.remove("X-Debug")
 * def ct = pm.request.headers.get("content-type")
 * ```
 *
 * @param headers Initial header pairs (name, value)
 */
class PmHeaderList(
    headers: List<Pair<String, String>> = emptyList()
) {

    private val headers: MutableList<Pair<String, String>> = headers.toMutableList()

    /** Appends a new header entry. Does not replace existing entries with the same name. */
    fun add(key: String, value: String) {
        headers.add(key to value)
    }

    /** Appends a new header entry from a map with "key" and "value" entries. */
    fun add(entry: Map<String, String>) {
        val key = entry["key"] ?: return
        val value = entry["value"] ?: return
        headers.add(key to value)
    }

    /**
     * Inserts or replaces a header. If a header with the same name (case-insensitive) exists,
     * it is replaced; otherwise, a new entry is appended.
     */
    fun upsert(key: String, value: String) {
        val idx = headers.indexOfFirst { it.first.equals(key, ignoreCase = true) }
        if (idx >= 0) {
            headers[idx] = key to value
        } else {
            headers.add(key to value)
        }
    }

    /** Removes all headers with the given name (case-insensitive). */
    fun remove(key: String) {
        headers.removeAll { it.first.equals(key, ignoreCase = true) }
    }

    /** Returns the value of the first header matching the given name (case-insensitive), or null. */
    fun get(key: String): String? = headers.find { it.first.equals(key, ignoreCase = true) }?.second

    /** Checks whether a header with the given name exists (case-insensitive). */
    fun has(key: String): Boolean = headers.any { it.first.equals(key, ignoreCase = true) }

    /** Returns all headers as a list of maps with "key" and "value" entries. */
    fun all(): List<Map<String, String>> = headers.map { mapOf("key" to it.first, "value" to it.second) }

    /** Returns an immutable snapshot of header pairs. */
    internal fun toPairs(): List<Pair<String, String>> = headers.toList()

    /** Replaces all headers with the given pairs. */
    internal fun fromPairs(pairs: List<Pair<String, String>>) {
        headers.clear()
        headers.addAll(pairs)
    }
}
