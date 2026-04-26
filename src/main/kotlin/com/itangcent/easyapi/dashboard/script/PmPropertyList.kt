package com.itangcent.easyapi.dashboard.script

/**
 * A mutable list of key-value-type property entries, used for URL-encoded and form-data parameters.
 *
 * Compatible with Postman's property list API. Each entry is a map with "key", "value", and "type" keys.
 *
 * Groovy usage:
 * ```groovy
 * pm.request.body.urlencoded.add("username", "alice")
 * pm.request.body.formdata.add("avatar", "@/photos/me.jpg", "file")
 * def username = pm.request.body.urlencoded.get("username")
 * ```
 *
 * @property items The backing mutable list of property entries
 */
class PmPropertyList(
    private val items: MutableList<Map<String, String>> = mutableListOf()
) {

    /** Adds a property entry as a map (must contain "key" and "value" entries). */
    fun add(entry: Map<String, String>) {
        items.add(entry)
    }

    /**
     * Adds a property entry with key, value, and optional type.
     *
     * @param key The parameter name
     * @param value The parameter value
     * @param type The parameter type (default: "text"; use "file" for file uploads)
     */
    fun add(key: String, value: String, type: String = "text") {
        items.add(mapOf("key" to key, "value" to value, "type" to type))
    }

    /** Retrieves the value for the first entry matching the given key, or null. */
    fun get(key: String): String? = items.find { it["key"] == key }?.get("value")

    /** Checks whether any entry has the given key. */
    fun has(key: String): Boolean = items.any { it["key"] == key }

    /** Returns an immutable snapshot of all entries. */
    fun all(): List<Map<String, String>> = items.toList()

    /** Removes all entries matching the given key. */
    fun remove(key: String) {
        items.removeAll { it["key"] == key }
    }

    /** Returns an immutable snapshot as a list. */
    internal fun toList(): List<Map<String, String>> = items.toList()
}
