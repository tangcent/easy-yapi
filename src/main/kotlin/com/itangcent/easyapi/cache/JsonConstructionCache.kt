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
 * - `className@option`
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
     * @return The cached model, or null if not found
     */
    fun get(classKey: String): Any? {
        return cache[classKey]
    }

    /**
     * Caches an object model.
     *
     * @param classKey The class identifier
     * @param value The model to cache
     */
    fun put(classKey: String, value: Any?) {
        cache[classKey] = value
    }

    /**
     * Clears all cached models.
     */
    fun clear() {
        cache.clear()
    }
}
