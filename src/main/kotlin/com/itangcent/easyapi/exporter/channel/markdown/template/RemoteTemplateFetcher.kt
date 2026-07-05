package com.itangcent.easyapi.exporter.channel.markdown.template

import com.itangcent.easyapi.http.HttpClient
import com.itangcent.easyapi.http.HttpRequest
import com.itangcent.easyapi.http.HttpResponse
import com.itangcent.easyapi.logging.IdeaLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Sealed result of [RemoteTemplateFetcher.fetch]. Never throws — failures are surfaced
 * as [Failed] so the resolver can log + fall through to the next tier.
 */
sealed class FetchResult {

    /** A successful fetch: the template text returned by the remote URL. */
    data class Ok(val text: String) : FetchResult()

    /**
     * A failed fetch. [reason] is a short human-readable string (logged via `IdeaConsole.warn`
     * by the resolver); [throwable] is present for transport/IO failures.
     */
    data class Failed(val reason: String, val throwable: Throwable? = null) : FetchResult()
}

/**
 * Fetches Markdown template text from a remote `http(s)` URL with SSRF hardening and a
 * per-session TTL cache .
 *
 * ## SSRF hardening
 *
 * - **Scheme allow-list**: only `http` and `https`; `file:`, `ftp:`, `data:`, `jar:`, etc.
 *   are rejected **before any connection is opened** .
 * - **Redirects disabled**: any 3xx response is treated as a fetch failure (review M1 — the
 *   shared `HttpClient` exposes no per-call redirect-policy hook, so disabling redirects
 *   is the achievable, stricter equivalent of per-redirect scheme inspection).
 * - **Size cap**: enforced via the `Content-Length` header when present (reject before
 *   trusting the body) + a post-read body-length check . Default 1 MiB.
 *   **Limitation:** the existing `HttpClient` materializes the full body before returning it
 *   (`ApacheHttpClient` / `IntelliJHttpClient` both read the whole entity), so this is not
 *   a true mid-stream abort — a hostile server can still force a full download up to its own
 *   limits before we discard it. True streaming protection would require extending
 *   `HttpClient`; deferred for v1.
 * - **Timeout**: caller passes an [HttpClient] configured with a bounded timeout (production
 *   uses `HttpClientProvider.getClient(httpTimeOut = 10)`).
 * - **Off-EDT**: the fetch runs on the provided [dispatcher] (production passes
 *   `IdeDispatchers.Background`).
 *
 * ## TTL cache 
 *
 * - Per-session `ConcurrentHashMap<URL, CachedEntry>` keyed by URL string.
 * - Default 10 min TTL (configurable via `markdown.template.url.ttl.seconds`).
 * - **Successful fetches only are cached** — a `Failed` is re-attempted on the next export
 *   so a transient network blip does not block the next export for the TTL window.
 *
 * ## Purity
 *
 * The fetcher is a pure object: it takes an injected [HttpClient] rather than reaching into
 * `HttpClientProvider` directly, so it is testable as pure JUnit (no `Project`,
 * no PSI). Production wiring is the resolver's responsibility.
 */
object RemoteTemplateFetcher : IdeaLog {

    /** Default TTL: 10 minutes . */
    const val DEFAULT_TTL_SECONDS: Long = 600L

    /** Default size cap: 1 MiB . */
    const val DEFAULT_MAX_BYTES: Long = 1_048_576L

    private val ALLOWED_SCHEMES = setOf("http", "https")

    /** Per-session cache keyed by URL string. Successful fetches only . */
    private val cache = ConcurrentHashMap<String, CachedEntry>()

    /**
     * Fetches the template text at [url].
     *
     * @param url The `http(s)` URL to fetch.
     * @param httpClient The HTTP client to use (production wraps
     *   `HttpClientProvider.getClient(httpTimeOut = 10)`; tests pass a fake).
     * @param ttlSeconds Cache TTL in seconds (default 600s = 10 min).
     * @param maxBytes Response size cap in bytes (default 1 MiB).
     * @param dispatcher The dispatcher to run the fetch on (production passes
     *   `IdeDispatchers.Background`; tests inject a recorder).
     * @return [FetchResult.Ok] on a 2xx response within the size cap; [FetchResult.Failed]
     *   otherwise. Never throws.
     */
    suspend fun fetch(
        url: String,
        httpClient: HttpClient,
        ttlSeconds: Long = DEFAULT_TTL_SECONDS,
        maxBytes: Long = DEFAULT_MAX_BYTES,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): FetchResult {
        // ── Scheme allow-list (SSRF guard — reject before connect) ──
        val scheme = try {
            URI(url).scheme?.lowercase()
        } catch (t: Throwable) {
            return FetchResult.Failed("invalid URL: ${t.message}", t)
        }
        if (scheme == null || scheme !in ALLOWED_SCHEMES) {
            return FetchResult.Failed(
                "scheme '${scheme ?: "null"}' not allowed; only http/https are permitted",
            )
        }

        // ── TTL cache hit (Ok results only — Failed is never cached) ──
        val now = System.currentTimeMillis()
        cache[url]?.let { cached ->
            if (!cached.isExpired(now)) {
                return FetchResult.Ok(cached.text)
            }
        }

        // ── Execute on the provided dispatcher (off-EDT) ──
        val result = withContext(dispatcher) {
            executeAndValidate(url, httpClient, maxBytes)
        }

        // ── Cache successful fetches only  ──
        if (result is FetchResult.Ok) {
            cache[url] = CachedEntry(
                text = result.text,
                expireAt = now + ttlSeconds * 1000L,
            )
        }
        return result
    }

    /**
     * Runs the HTTP request and validates the response against the size cap + status rules.
     * Called inside `withContext(dispatcher)`. Never throws — failures become [FetchResult.Failed].
     */
    private suspend fun executeAndValidate(
        url: String,
        httpClient: HttpClient,
        maxBytes: Long,
    ): FetchResult {
        val response: HttpResponse = try {
            httpClient.execute(HttpRequest(url = url, method = "GET"))
        } catch (t: Throwable) {
            return FetchResult.Failed("transport error fetching $url: ${t.message}", t)
        }

        // ── 3xx → Failed (redirects disabled) ──
        if (response.code in 300..399) {
            return FetchResult.Failed(
                "redirect response ${response.code} not followed (redirects disabled)",
            )
        }

        // ── Non-2xx → Failed  ──
        if (response.code !in 200..299) {
            return FetchResult.Failed("HTTP ${response.code} fetching $url")
        }

        // ── Content-Length header check (reject before trusting the body) ──
        response.headers.entries
            .firstOrNull { it.key.equals("Content-Length", ignoreCase = true) }
            ?.value?.firstOrNull()
            ?.toLongOrNull()
            ?.let { declared ->
                if (declared > maxBytes) {
                    return FetchResult.Failed(
                        "response size $declared bytes exceeds cap $maxBytes (Content-Length)",
                    )
                }
            }

        // ── Post-read body length check  ──
        val body = response.body ?: ""
        if (body.length.toLong() > maxBytes) {
            return FetchResult.Failed(
                "response body ${body.length} bytes exceeds cap $maxBytes",
            )
        }

        return FetchResult.Ok(body)
    }

    /**
     * Test-only — clears the per-session cache so each test starts from a clean state.
     * Production never calls this; cache lifetime == IDE session lifetime.
     */
    internal fun clearCacheForTesting() {
        cache.clear()
    }

    /** Cached successful fetch with its expiry timestamp. */
    private data class CachedEntry(
        val text: String,
        val expireAt: Long,
    ) {
        fun isExpired(now: Long): Boolean = now >= expireAt
    }
}
