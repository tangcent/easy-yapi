package com.itangcent.easyapi.cache

import java.util.concurrent.ConcurrentHashMap

/**
 * Cache for JSON object model construction.
 *
 * Used during [DefaultPsiClassHelper.buildObjectModel] to avoid
 * redundant processing of the same class. The cache is scoped
 * to a single build operation and cleared afterward.
 *
 * ## Cache Key Format
 * - Without group: `className@option`
 * - With group: `group:className@option`
 *
 * ## Thread Safety
 * Uses ConcurrentHashMap for thread-safe access.
 *
 * @see com.itangcent.easyapi.psi.DefaultPsiClassHelper for usage
 */
class JsonConstructionCache {
    private val cache = ConcurrentHashMap<String, Any?>()

    /**
     * Gets a cached object model.
     *
     * @param classKey The class identifier (e.g., "com.example.User@15")
     * @param group Optional group name for namespacing
     * @return The cached model, or null if not found
     */
    fun get(classKey: String, group: String?): Any? {
        return cache[cacheKey(classKey, group)]
    }

    /**
     * Caches an object model.
     *
     * @param classKey The class identifier
     * @param group Optional group name for namespacing
     * @param value The model to cache
     */
    fun put(classKey: String, group: String?, value: Any?) {
        cache[cacheKey(classKey, group)] = value
    }

    /**
     * Clears all cached models.
     */
    fun clear() {
        cache.clear()
    }

    private fun cacheKey(classKey: String, group: String?): String {
        return if (group.isNullOrBlank()) classKey else "$group:$classKey"
    }
}

