package com.itangcent.easyapi.config.resource

import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.http.HttpRequest
import com.itangcent.easyapi.http.UrlConnectionHttpClient
import com.itangcent.easyapi.logging.IdeaConsole
import com.itangcent.easyapi.util.storage.LocalStorage
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

/**
 * Resolves and caches remote resources.
 *
 * Fetches content from URLs and caches the results with a configurable TTL.
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
 * - Default TTL: 2 hours
 *
 * @param localStorage The local storage for persisting fetched content
 * @param console Optional console for logging warnings
 * @param ttlMs Time-to-live in milliseconds (default: 2 hours)
 */
class CachedResourceResolver(
    private val localStorage: LocalStorage,
    private val console: IdeaConsole? = null,
    private val ttlMs: Long = 2 * 60 * 60 * 1000L,
    private val fetchTimeoutMs: Long = 30_000L
) {
    suspend fun get(url: String): String? {
        val cached = localStorage.get(CACHE_GROUP, url)?.toString()
        if (cached != null) {
            val fetchedAt = localStorage.get(TIMESTAMP_GROUP, url)?.toString()?.toLongOrNull()
            if (fetchedAt != null && System.currentTimeMillis() - fetchedAt <= ttlMs) {
                return cached
            }
        }

        val content = runCatching { fetch(url) }
            .onFailure { e ->
                if (e is TimeoutCancellationException) {
                    console?.warn("Remote config fetch timed out (${fetchTimeoutMs}ms): $url")
                } else {
                    console?.warn("Remote config unreachable: $url", e)
                }
            }
            .getOrNull()
            ?: return cached

        localStorage.set(CACHE_GROUP, url, content)
        localStorage.set(TIMESTAMP_GROUP, url, System.currentTimeMillis().toString())
        return content
    }

    private suspend fun fetch(url: String): String {
        return withTimeout(fetchTimeoutMs.milliseconds) {
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
    }
}
