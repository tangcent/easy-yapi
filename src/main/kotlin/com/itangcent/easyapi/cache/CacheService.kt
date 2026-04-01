package com.itangcent.easyapi.cache

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cache service for project-level data.
 *
 * A simple key-value cache that persists only for the current session.
 * Uses ConcurrentHashMap for thread-safe access.
 *
 * ## Usage
 * ```kotlin
 * val cache = CacheService.getInstance(project)
 *
 * // Store a value
 * cache.putString("api.lastExport", "2024-01-01")
 *
 * // Retrieve a value
 * val lastExport = cache.getString("api.lastExport")
 * ```
 *
 * @see CacheRepository for persistent file-based cache
 */
@Service(Service.Level.PROJECT)
class CacheService(@Suppress("UNUSED_PARAMETER") project: Project) {

    companion object {
        /**
         * Gets the CacheService instance for the project.
         */
        fun getInstance(project: Project): CacheService = project.getService(CacheService::class.java)
    }
    private val strings = ConcurrentHashMap<String, String>()

    /**
     * Gets a cached string value.
     *
     * @param key The cache key
     * @return The cached value, or null if not found
     */
    fun getString(key: String): String? = strings[key]

    /**
     * Stores a string value in the cache.
     *
     * @param key The cache key
     * @param value The value to store
     */
    fun putString(key: String, value: String) {
        strings[key] = value
    }
}
