package com.itangcent.easyapi.cache

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Project-scoped cache repository.
 *
 * Stores cache files under `.idea/easyapi-cache/` in the project directory.
 * This cache is specific to each project and is typically used for:
 * - API export history
 * - Project-specific settings cache
 * - Temporary build artifacts
 *
 * ## Location
 * - Default: `<project>/.idea/easyapi-cache/`
 * - Fallback: System temp directory if project has no base path
 *
 * @see CacheRepository for the interface
 * @see AppCacheRepository for application-wide cache
 */
@Service(Service.Level.PROJECT)
class ProjectCacheRepository(private val project: Project) : CacheRepository {

    private val cacheDir: Path by lazy {
        project.basePath?.let { Paths.get(it, ".idea", "easyapi-cache") }
            ?: Paths.get(System.getProperty("java.io.tmpdir"), "easyapi-cache")
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
        fun getInstance(project: Project): ProjectCacheRepository =
            project.getService(ProjectCacheRepository::class.java)
    }
}
