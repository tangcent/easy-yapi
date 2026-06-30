package com.itangcent.easyapi.config.resource

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.logging.IdeaLog
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * A configuration resource loaded from a local file path or remote URL.
 *
 * @property content The raw text content of the resource
 * @property baseDir The base directory for resolving relative includes inside
 *           [content]; `null` when there is no meaningful base (e.g. the
 *           resource was loaded from a bare path with no parent).
 */
data class LoadedConfigResource(
    val content: String,
    val baseDir: String?
)

/**
 * Loads configuration content from a local file path or remote URL.
 *
 * Supported inputs:
 * - **Remote URLs** (`http://` / `https://`) — fetched via
 *   [CachedResourceResolver] obtained from the [project].
 * - **Local paths** — resolved relative to [baseDir] (or the user home for
 *   `~/` prefixes, or as absolute paths).
 *
 * Returns `null` when the resource cannot be read or the scheme is not
 * supported. Callers decide whether `null` is an error based on directive
 * state (e.g. `ignoreNotFoundFile`).
 *
 * This is a project-level service scoped to a specific IntelliJ project.
 */
@Service(Service.Level.PROJECT)
class ConfigResourceLoader(
    private val project: Project
) : IdeaLog {

    /**
     * Loads the resource at [pathOrUrl].
     *
     * @param pathOrUrl A local file path or a remote URL.
     * @param baseDir The base directory for resolving relative paths.
     * @return The loaded resource, or `null` if it could not be read.
     */
    suspend fun load(pathOrUrl: String, baseDir: String?): LoadedConfigResource? {
        val p = pathOrUrl.trim().trim('"', '\'')

        if (p.startsWith("http://") || p.startsWith("https://")) {
            val resolver = CachedResourceResolver.getInstance(project)
            val content = resolver.get(p) ?: return null
            return LoadedConfigResource(content, parentUrl(p))
        }

        val resolved = resolveLocalPath(p, baseDir) ?: return null
        return runCatching {
            val content = Files.readString(resolved, Charsets.UTF_8)
            LoadedConfigResource(content, resolved.parent?.toString())
        }.onFailure {
            LOG.warn("ConfigResourceLoader: failed to read $resolved", it)
        }.getOrNull()
    }

    private fun resolveLocalPath(path: String, baseDir: String?): Path? {
        val resolved = when {
            path.startsWith("~/") -> Paths.get(System.getProperty("user.home")).resolve(path.removePrefix("~/"))
            Paths.get(path).isAbsolute -> Paths.get(path)
            baseDir != null -> Paths.get(baseDir).resolve(path)
            else -> Paths.get(path)
        }
        return resolved.normalize()
    }

    /**
     * Returns the parent URL of [url] by stripping the last path segment after
     * the host. When the URL has no path (e.g. `https://example.com`), the URL
     * itself is returned so relative includes resolve against the host root.
     */
    private fun parentUrl(url: String): String {
        val schemeEnd = url.indexOf("://")
        val pathStart = if (schemeEnd >= 0) schemeEnd + 3 else 0
        val lastSlash = url.lastIndexOf('/')
        return if (lastSlash > pathStart) url.substring(0, lastSlash) else url
    }

    companion object {
        fun getInstance(project: Project): ConfigResourceLoader = project.service()
    }
}
