package com.itangcent.easyapi.cache

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Application-scoped cache repository.
 *
 * Stores cache files under `~/.easy-api/cache/` in the user's home directory.
 * This cache is shared across all projects and is typically used for:
 * - AI API response cache
 * - Global settings cache
 * - Shared templates
 *
 * ## Location
 * - Default: `~/.easy-api/cache/`
 *
 * @see CacheRepository for the interface
 * @see ProjectCacheRepository for project-specific cache
 */
@Service(Service.Level.APP)
class AppCacheRepository : CacheRepository {

    private val cacheDir: Path by lazy {
        val home = System.getProperty("user.home")
        Paths.get(home, ".easy-api", "cache")
    }

    override fun resolve(path: String): Path {
        val resolved = cacheDir.resolve(path)
        resolved.parent?.let { Files.createDirectories(it) }
        return resolved
    }

    override fun read(key: String): String? {
        val file = cacheDir.resolve(key)
        return runCatching {
            if (!Files.exists(file)) return@runCatching null
            Files.readString(file, Charsets.UTF_8)
        }.getOrNull()
    }

    override fun write(key: String, content: String) {
        val file = cacheDir.resolve(key)
        runCatching {
            Files.createDirectories(file.parent)
            Files.writeString(file, content, Charsets.UTF_8)
        }
    }

    override fun delete(key: String) {
        val file = cacheDir.resolve(key)
        runCatching { Files.deleteIfExists(file) }
    }

    override fun clear() {
        runCatching {
            val file = cacheDir.toFile()
            if (file.exists()) {
                file.deleteRecursively()
            }
        }
    }

    override fun cacheSize(): Long {
        val dir = cacheDir.toFile()
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    companion object {
        fun getInstance(): AppCacheRepository =
            ApplicationManager.getApplication().getService(AppCacheRepository::class.java)
    }
}
