package com.itangcent.easyapi.config.resource

import com.itangcent.easyapi.cache.CacheService
import com.itangcent.easyapi.http.HttpRequest
import com.itangcent.easyapi.http.UrlConnectionHttpClient
import com.itangcent.easyapi.logging.IdeaConsole

/**
 * Resolves and caches remote resources.
 *
 * Fetches content from URLs and caches the results with a configurable TTL.
 * On cache miss or expiration, fetches fresh content; on failure, falls back
 * to stale cache.
 *
 * ## Caching Behavior
 * - Cache key: `remote:<url>`
 * - Timestamp key: `remote-ts:<url>`
 * - Default TTL: 2 hours
 *
 * @param cacheService The cache service for storing fetched content
 * @param console Optional console for logging warnings
 * @param ttlMs Time-to-live in milliseconds (default: 2 hours)
 */
class CachedResourceResolver(
    private val cacheService: CacheService,
    private val console: IdeaConsole? = null,
    private val ttlMs: Long = 2 * 60 * 60 * 1000L
) {
    suspend fun get(url: String): String? {
        val cached = cacheService.getString(cacheKey(url))
        if (cached != null) {
            val fetchedAt = cacheService.getString(cacheTimeKey(url))?.toLongOrNull()
            if (fetchedAt != null && System.currentTimeMillis() - fetchedAt <= ttlMs) {
                return cached
            }
        }

        val content = runCatching { fetch(url) }
            .onFailure { e -> console?.warn("Remote config unreachable: $url", e) }
            .getOrNull()
            ?: return cached // fall back to stale cache on failure

        cacheService.putString(cacheKey(url), content)
        cacheService.putString(cacheTimeKey(url), System.currentTimeMillis().toString())
        return content
    }

    private fun cacheKey(url: String): String = "remote:$url"
    private fun cacheTimeKey(url: String): String = "remote-ts:$url"

    private suspend fun fetch(url: String): String {
        val request = HttpRequest(
            url = url,
            method = "GET",
            headers = listOf("Accept" to "text/plain, */*")
        )
        val response = UrlConnectionHttpClient.execute(request)
        return response.body
            ?: throw IllegalStateException("Empty response from $url, code=${response.code}")
    }
}
