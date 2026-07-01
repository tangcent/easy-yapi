package com.itangcent.easyapi.exporter.markdown.template

import com.itangcent.easyapi.http.HttpClient
import com.itangcent.easyapi.http.HttpRequest
import com.itangcent.easyapi.http.HttpResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pins the contract of [RemoteTemplateFetcher] before the implementation exists
 * (test-first, design.md § Test Strategy + AGENTS.md `write-test-case` skill).
 *
 * Pure JUnit (Pattern A) — no `Project`, no PSI (NFR-4). The fetcher is a pure object
 * that takes an injected [HttpClient]; production wraps `HttpClientProvider.getClient(...)`
 * around it, tests pass a fake.
 *
 * Covers Req 7 (Remote Template Source) + NFR-5 (SSRF hardening):
 *  - 2xx → `FetchResult.Ok(body)` 
 *  - non-2xx → `Failed` 
 *  - `Content-Length` > cap → `Failed` (reject before reading body) 
 *  - body length > cap → `Failed` (post-read check) 
 *  - 3xx redirect → `Failed` (redirects disabled, NFR-5 / review M1)
 *  - `file:`/`ftp:`/`data:` schemes → `Failed` (rejected before connect) 
 *  - TTL cache hit on second fetch within TTL 
 *  - `Failed` results **not** cached (re-attempted next export, Req 7.3 refined)
 *  - fetch runs on the provided dispatcher (off-EDT, NFR-5 / design § Security)
 *
 * _Requirements: 7.1, 7.3, 7.4, 7.5, 7.6; NFR-5_
 */
class RemoteTemplateFetcherTest {

    // ────────────────────────────── Fakes ──────────────────────────────

    /**
     * Queue-backed [HttpClient]: each [execute] call pops the next queued response.
     * Tracks [executeCount] so tests can assert cache hits (count stays at 1) vs.
     * re-attempts (count increments).
     */
    private class FakeHttpClient : HttpClient {
        private val responses = ArrayDeque<HttpResponse>()
        var executeCount: Int = 0
            private set
        var lastRequest: HttpRequest? = null
            private set
        var throwOnExecute: Throwable? = null

        fun enqueue(response: HttpResponse) {
            responses.addLast(response)
        }

        override suspend fun execute(request: HttpRequest): HttpResponse {
            executeCount++
            lastRequest = request
            throwOnExecute?.let { throw it }
            return responses.removeFirstOrNull()
                ?: error("No queued response for request: $request")
        }

        override fun close() {}
    }

    /**
     * A [CoroutineDispatcher] that records whether [dispatch] was invoked, proving the
     * fetcher delegated execution to the injected dispatcher (off-EDT contract).
     */
    private class RecordingDispatcher : CoroutineDispatcher() {
        var dispatchCount: Int = 0
            private set

        override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
            dispatchCount++
            block.run() // run inline for determinism
        }
    }

    @Before
    fun clearCache() {
        // Cache isolation between tests — the fetcher holds a per-session ConcurrentHashMap.
        RemoteTemplateFetcher.clearCacheForTesting()
    }

    private val okBody: String = "# {{moduleName}}\n\nTemplate from a remote URL.\n"

    private fun okResponse(body: String = okBody, headers: Map<String, List<String>> = emptyMap()): HttpResponse =
        HttpResponse(code = 200, headers = headers, body = body)

    // ─────────────────────────── Success path ──────────────────────────

    @Test
    fun test2xxReturnsOkWithBody() = runBlocking {
        val client = FakeHttpClient().apply { enqueue(okResponse()) }

        val result = RemoteTemplateFetcher.fetch(
            url = "https://example.com/template.md",
            httpClient = client,
        )

        assertTrue("2xx should yield Ok", result is FetchResult.Ok)
        assertEquals(okBody, (result as FetchResult.Ok).text)
        assertEquals(1, client.executeCount)
    }

    @Test
    fun testFetchUsesHttpGetMethod() = runBlocking {
        val client = FakeHttpClient().apply { enqueue(okResponse()) }

        RemoteTemplateFetcher.fetch("https://example.com/t.md", client)

        val req = client.lastRequest
        assertNotNull(req)
        assertEquals("GET", req!!.method)
        assertEquals("https://example.com/t.md", req.url)
    }

    // ─────────────────────────── Non-2xx failure ───────────────────────

    @Test
    fun test404ReturnsFailed() = runBlocking {
        val client = FakeHttpClient().apply {
            enqueue(HttpResponse(code = 404, body = "Not Found"))
        }

        val result = RemoteTemplateFetcher.fetch("https://example.com/missing.md", client)

        assertTrue("404 should yield Failed", result is FetchResult.Failed)
        val failed = result as FetchResult.Failed
        assertTrue("reason should mention 404", failed.reason.contains("404"))
        assertNull("404 has no throwable", failed.throwable)
    }

    @Test
    fun test500ReturnsFailed() = runBlocking {
        val client = FakeHttpClient().apply {
            enqueue(HttpResponse(code = 500, body = "Internal Server Error"))
        }

        val result = RemoteTemplateFetcher.fetch("https://example.com/t.md", client)

        assertTrue(result is FetchResult.Failed)
        assertTrue((result as FetchResult.Failed).reason.contains("500"))
    }

    // ──────────────────────── Size cap enforcement ─────────────────────

    @Test
    fun testContentLengthHeaderExceedsCapReturnsFailed() = runBlocking {
        val cap = 100L
        // Content-Length header exceeds the cap — must be rejected before reading body.
        val client = FakeHttpClient().apply {
            enqueue(HttpResponse(
                code = 200,
                headers = mapOf("Content-Length" to listOf("101")),
                body = "x".repeat(50), // body smaller than cap; header is what triggers rejection
            ))
        }

        val result = RemoteTemplateFetcher.fetch(
            url = "https://example.com/t.md",
            httpClient = client,
            maxBytes = cap,
        )

        assertTrue("Content-Length > cap should yield Failed", result is FetchResult.Failed)
        val failed = result as FetchResult.Failed
        assertTrue(
            "reason should mention size/cap: ${failed.reason}",
            failed.reason.contains("size", ignoreCase = true) ||
                failed.reason.contains("cap", ignoreCase = true) ||
                failed.reason.contains("Content-Length", ignoreCase = true),
        )
    }

    @Test
    fun testBodyLengthExceedsCapReturnsFailed() = runBlocking {
        val cap = 100L
        // No Content-Length header; body itself exceeds the cap.
        val oversizeBody = "x".repeat(101)
        val client = FakeHttpClient().apply {
            enqueue(HttpResponse(code = 200, body = oversizeBody))
        }

        val result = RemoteTemplateFetcher.fetch(
            url = "https://example.com/t.md",
            httpClient = client,
            maxBytes = cap,
        )

        assertTrue("body > cap should yield Failed", result is FetchResult.Failed)
    }

    @Test
    fun testBodyExactlyAtCapReturnsOk() = runBlocking {
        val cap = 100L
        val atCapBody = "x".repeat(100)
        val client = FakeHttpClient().apply {
            enqueue(HttpResponse(code = 200, body = atCapBody))
        }

        val result = RemoteTemplateFetcher.fetch(
            url = "https://example.com/t.md",
            httpClient = client,
            maxBytes = cap,
        )

        assertTrue("body == cap should yield Ok", result is FetchResult.Ok)
    }

    // ──────────────────────── 3xx redirect → Failed ───────────────────

    @Test
    fun test301RedirectReturnsFailed() = runBlocking {
        // Redirects are disabled (NFR-5 / review M1): any 3xx is a failure, not followed.
        val client = FakeHttpClient().apply {
            enqueue(HttpResponse(
                code = 301,
                headers = mapOf("Location" to listOf("https://evil.example/file-redirect")),
                body = "Moved Permanently",
            ))
        }

        val result = RemoteTemplateFetcher.fetch("https://example.com/t.md", client)

        assertTrue("3xx should yield Failed (redirects disabled)", result is FetchResult.Failed)
        val failed = result as FetchResult.Failed
        assertTrue(
            "reason should mention redirect/3xx: ${failed.reason}",
            failed.reason.contains("3", ignoreCase = true) ||
                failed.reason.contains("redirect", ignoreCase = true),
        )
    }

    @Test
    fun test302RedirectReturnsFailed() = runBlocking {
        val client = FakeHttpClient().apply {
            enqueue(HttpResponse(code = 302, headers = mapOf("Location" to listOf("https://other.example/t")), body = "Found"))
        }

        val result = RemoteTemplateFetcher.fetch("https://example.com/t.md", client)

        assertTrue(result is FetchResult.Failed)
    }

    // ──────────────────── Scheme allow-list (SSRF) ────────────────────

    @Test
    fun testFileSchemeRejectedBeforeConnect() = runBlocking {
        val client = FakeHttpClient().apply {
            // Even if a response is queued, the scheme check happens first.
            enqueue(okResponse())
        }

        val result = RemoteTemplateFetcher.fetch("file:///etc/passwd", client)

        assertTrue("file: scheme should yield Failed", result is FetchResult.Failed)
        val failed = result as FetchResult.Failed
        assertTrue(
            "reason should mention scheme: ${failed.reason}",
            failed.reason.contains("scheme", ignoreCase = true),
        )
        assertEquals(
            "must NOT call execute for a rejected scheme",
            0, client.executeCount,
        )
    }

    @Test
    fun testFtpSchemeRejectedBeforeConnect() = runBlocking {
        val client = FakeHttpClient().apply { enqueue(okResponse()) }

        val result = RemoteTemplateFetcher.fetch("ftp://example.com/t.md", client)

        assertTrue(result is FetchResult.Failed)
        assertEquals(0, client.executeCount)
    }

    @Test
    fun testDataSchemeRejectedBeforeConnect() = runBlocking {
        val client = FakeHttpClient().apply { enqueue(okResponse()) }

        val result = RemoteTemplateFetcher.fetch("data:text/plain,hello", client)

        assertTrue(result is FetchResult.Failed)
        assertEquals(0, client.executeCount)
    }

    @Test
    fun testJarSchemeRejectedBeforeConnect() = runBlocking {
        val client = FakeHttpClient().apply { enqueue(okResponse()) }

        val result = RemoteTemplateFetcher.fetch("jar:file:/x.jar!/t.md", client)

        assertTrue(result is FetchResult.Failed)
        assertEquals(0, client.executeCount)
    }

    @Test
    fun testHttpSchemeAccepted() = runBlocking {
        val client = FakeHttpClient().apply { enqueue(okResponse()) }

        val result = RemoteTemplateFetcher.fetch("http://example.com/t.md", client)

        assertTrue("http: scheme should be accepted", result is FetchResult.Ok)
        assertEquals(1, client.executeCount)
    }

    @Test
    fun testHttpsSchemeAccepted() = runBlocking {
        val client = FakeHttpClient().apply { enqueue(okResponse()) }

        val result = RemoteTemplateFetcher.fetch("https://example.com/t.md", client)

        assertTrue("https: scheme should be accepted", result is FetchResult.Ok)
    }

    // ─────────────────────────── TTL cache ────────────────────────────

    @Test
    fun testSuccessfulFetchCachedWithinTtl() = runBlocking {
        // Req 7.3: repeated exports in the same session do not re-download.
        val client = FakeHttpClient().apply { enqueue(okResponse()) }

        val url = "https://example.com/cached.md"
        val r1 = RemoteTemplateFetcher.fetch(url, client, ttlSeconds = 600)
        val r2 = RemoteTemplateFetcher.fetch(url, client, ttlSeconds = 600)

        assertTrue(r1 is FetchResult.Ok)
        assertTrue(r2 is FetchResult.Ok)
        assertEquals(
            "second fetch within TTL should hit cache (no execute call)",
            1, client.executeCount,
        )
        assertEquals((r1 as FetchResult.Ok).text, (r2 as FetchResult.Ok).text)
    }

    @Test
    fun testFailedFetchNotCachedReattemptedNextExport() = runBlocking {
        // Req 7.3 refined: cache Ok only; Failed is re-attempted next export so a
        // transient network blip doesn't block the next export for the full TTL window.
        val client = FakeHttpClient().apply {
            enqueue(HttpResponse(code = 500, body = "transient"))
            enqueue(okResponse()) // second attempt succeeds
        }

        val url = "https://example.com/flaky.md"
        val r1 = RemoteTemplateFetcher.fetch(url, client, ttlSeconds = 600)
        val r2 = RemoteTemplateFetcher.fetch(url, client, ttlSeconds = 600)

        assertTrue("first attempt should fail", r1 is FetchResult.Failed)
        assertTrue("second attempt should succeed (Failed not cached)", r2 is FetchResult.Ok)
        assertEquals(
            "Failed must not be cached — execute should be called twice",
            2, client.executeCount,
        )
    }

    @Test
    fun testCacheHitIsPerUrl() = runBlocking {
        val client = FakeHttpClient().apply {
            enqueue(okResponse("# A\n"))
            enqueue(okResponse("# B\n"))
        }

        val r1 = RemoteTemplateFetcher.fetch("https://example.com/a.md", client)
        val r2 = RemoteTemplateFetcher.fetch("https://example.com/b.md", client)

        assertTrue(r1 is FetchResult.Ok)
        assertTrue(r2 is FetchResult.Ok)
        assertEquals("# A\n", (r1 as FetchResult.Ok).text)
        assertEquals("# B\n", (r2 as FetchResult.Ok).text)
        assertEquals(2, client.executeCount)
    }

    // ───────────────────────── Network/IO failure ─────────────────────

    @Test
    fun testHttpClientThrowsReturnsFailedWithThrowable() = runBlocking {
        val client = FakeHttpClient().apply {
            throwOnExecute = java.io.IOException("connection reset")
        }

        val result = RemoteTemplateFetcher.fetch("https://example.com/t.md", client)

        assertTrue("transport exception should yield Failed", result is FetchResult.Failed)
        val failed = result as FetchResult.Failed
        assertNotNull("throwable should be preserved", failed.throwable)
        assertTrue(failed.throwable is java.io.IOException)
    }

    // ────────────────────────── Dispatcher (off-EDT) ──────────────────

    @Test
    fun testFetchRunsOnProvidedDispatcher() = runBlocking {
        val dispatcher = RecordingDispatcher()
        val client = FakeHttpClient().apply { enqueue(okResponse()) }

        RemoteTemplateFetcher.fetch(
            url = "https://example.com/t.md",
            httpClient = client,
            dispatcher = dispatcher,
        )

        assertTrue(
            "fetch should delegate to the provided dispatcher (off-EDT contract)",
            dispatcher.dispatchCount > 0,
        )
    }
}
