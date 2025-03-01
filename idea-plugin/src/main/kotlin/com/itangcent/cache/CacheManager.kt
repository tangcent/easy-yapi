package com.itangcent.cache

import com.google.inject.ImplementedBy
import com.google.inject.Singleton

/**
 * Interface that provides a way to check if caching is currently enabled.
 *
 * @author tangcent
 * @date 2025/02/18
 */
@ImplementedBy(DefaultCacheManager::class)
interface CacheIndicator {

    /**
     * Indicates whether caching is currently enabled.
     *
     * @return true if caching is enabled, false otherwise
     */
    val useCache: Boolean
}

/**
 * Interface that provides methods to control caching behavior.
 * It allows enabling and disabling of caching functionality at runtime.
 */
@ImplementedBy(DefaultCacheManager::class)
interface CacheSwitcher {

    /**
     * Disables the use of cache for subsequent operations.
     * After calling this method, caching will be disabled until [useCache] is called.
     */
    fun notUseCache()

    /**
     * Enables the use of cache for subsequent operations.
     * After calling this method, caching will be enabled until [notUseCache] is called.
     */
    fun useCache()
}

/**
 * Combined interface that provides both cache state indication and control capabilities.
 * This interface combines [CacheIndicator] and [CacheSwitcher] to provide a complete
 * cache management solution.
 */
@ImplementedBy(DefaultCacheManager::class)
interface CacheManager : CacheIndicator, CacheSwitcher

/**
 * Extension function that temporarily disables caching for a block of code.
 *
 * This function provides a convenient way to execute code with caching disabled,
 * ensuring that caching is restored to its previous state after execution,
 * even if an exception occurs.
 *
 * @param call The block of code to execute with caching disabled
 */
fun <T> CacheSwitcher.withoutCache(call: () -> T): T {
    this.notUseCache()
    try {
        return call()
    } finally {
        this.useCache()
    }
}

/**
 * Default implementation of [CacheManager] interface.
 */
@Singleton
class DefaultCacheManager : CacheManager {

    /**
     * Current caching state. Defaults to true (caching enabled).
     */
    override var useCache: Boolean = true

    override fun notUseCache() {
        useCache = false
    }

    override fun useCache() {
        useCache = true
    }
}