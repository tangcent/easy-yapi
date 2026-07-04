package com.itangcent.easyapi.config.resource

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.service
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.http.HttpRequest
import com.itangcent.easyapi.http.UrlConnectionHttpClient
import com.itangcent.easyapi.logging.IdeaConsole
import com.itangcent.easyapi.logging.console
import com.itangcent.easyapi.settings.module.HttpSettings
import com.itangcent.easyapi.settings.settings
import com.itangcent.easyapi.util.storage.LocalStorage
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

/**
 * Resolves and caches remote resources.
 *
 * Fetches content from URLs and caches the results with a fixed TTL.
 * On cache miss or expiration, fetches fresh content; on failure, falls back
 * to stale cache.
 *
 * Uses [LocalStorage] for persistence across IDE sessions, so cached remote
 * configs survive restarts and are available even when the remote server is
 * unreachable.
 *
 * ## Caching Behavior
 * - Cache group: `remote-cache`
 * - Timestamp group: `remote-cache-ts`
 * - TTL: 2 hours (constant)
 * - Fetch timeout: read from [HttpSettings.httpTimeOut] (seconds → ms)
 *
 * This is a project-level service scoped to a specific IntelliJ project.
 */
@Service(Service.Level.PROJECT)
class CachedResourceResolver(
    private val project: Project
) {
    private val localStorage = LocalStorage.getInstance(project)
    private val console: IdeaConsole get() = project.console

    private fun fetchTimeoutMs(): Long =
        project.settings<HttpSettings>().httpTimeOut.toLong() * 1000L

    suspend fun get(url: String): String? {
        val cached = localStorage.get(CACHE_GROUP, url)?.toString()
        if (cached != null) {
            val fetchedAt = localStorage.get(TIMESTAMP_GROUP, url)?.toString()?.toLongOrNull()
            if (fetchedAt != null && System.currentTimeMillis() - fetchedAt <= TTL_MS) {
                return cached
            }
        }

        val timeoutMs = fetchTimeoutMs()
        val content = runCatching { fetch(url, timeoutMs) }
            .onFailure { e ->
                if (e is TimeoutCancellationException) {
                    console.warn("Remote config fetch timed out (${timeoutMs}ms): $url")
                } else {
                    console.warn("Remote config unreachable: $url", e)
                }
            }
            .getOrNull()
            ?: return cached

        localStorage.set(CACHE_GROUP, url, content)
        localStorage.set(TIMESTAMP_GROUP, url, System.currentTimeMillis().toString())
        return content
    }

    private suspend fun fetch(url: String, timeoutMs: Long): String {
        return withTimeout(timeoutMs.milliseconds) {
            withContext(IdeDispatchers.Background) {
                val request = HttpRequest(
                    url = url,
                    method = "GET",
                    headers = listOf("Accept" to "text/plain, */*")
                )
                val response = UrlConnectionHttpClient.execute(request)
                response.body
                    ?: throw IllegalStateException("Empty response from $url, code=${response.code}")
            }
        }
    }

    companion object {
        private const val CACHE_GROUP = "remote-cache"
        private const val TIMESTAMP_GROUP = "remote-cache-ts"

        /** Cache time-to-live: 2 hours. */
        private const val TTL_MS = 2 * 60 * 60 * 1000L

        fun getInstance(project: Project): CachedResourceResolver = project.service()
    }
}
