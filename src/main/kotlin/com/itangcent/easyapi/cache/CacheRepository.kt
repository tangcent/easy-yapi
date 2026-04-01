package com.itangcent.easyapi.cache

import java.nio.file.Path

/**
 * Unified interface for file-based cache storage.
 *
 * Two implementations:
 * - [ProjectCacheRepository] — project-scoped, stored under `.idea/easyapi-cache/`
 * - [AppCacheRepository] — global, stored under `~/.easy-api/cache/`
 */
interface CacheRepository {

    /**
     * Resolve a relative [path] to an absolute [Path] within this repository's cache directory.
     * The parent directories are created if they don't exist.
     */
    fun resolve(path: String): Path

    fun read(key: String): String?

    fun write(key: String, content: String)

    fun delete(key: String)

    fun clear()

    fun cacheSize(): Long
}
