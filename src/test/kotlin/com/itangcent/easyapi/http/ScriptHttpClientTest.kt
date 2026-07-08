package com.itangcent.easyapi.http

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Tests for [ScriptHttpClient] (Spec: ai-workflow-patterns, T2.1 / D1).
 *
 * Verifies the sync adapter:
 *  - returns the delegate's [HttpResponse]
 *  - does NOT deadlock when called from a background thread (the JSR-223 script runtime
 *    evaluates `http.call.after` on [com.itangcent.easyapi.core.threading.IdeDispatchers.Background])
 */
class ScriptHttpClientTest {

    @Test
    fun `executeSync returns the delegate's HttpResponse`() = runBlocking {
        val request = HttpRequest(url = "https://refresh.example.com/token", method = "POST", body = "grant_type=refresh_token")
        val expectedResponse = HttpResponse(code = 200, body = """{"access_token":"newTok"}""")
        val delegate = mock<HttpClient>()
        whenever(delegate.execute(request)).thenReturn(expectedResponse)

        val scriptClient = ScriptHttpClient(delegate)
        val response = scriptClient.executeSync(request)

        assertEquals(200, response.code)
        assertEquals("""{"access_token":"newTok"}""", response.body)
    }

    @Test
    fun `executeSync handles null body in response`() = runBlocking {
        val request = HttpRequest(url = "https://example.com", method = "GET")
        val expectedResponse = HttpResponse(code = 204, body = null)
        val delegate = mock<HttpClient>()
        whenever(delegate.execute(request)).thenReturn(expectedResponse)

        val response = ScriptHttpClient(delegate).executeSync(request)
        assertEquals(204, response.code)
        assertEquals(null, response.body)
    }

    @Test
    fun `executeSync does not deadlock when called from a background thread`() {
        // Mirrors the real call site: http.call.after runs on IdeDispatchers.Background.
        // We simulate that with a plain background thread + CountDownLatch; if runBlocking
        // deadlocks, the latch never counts down and the test times out.
        val request = HttpRequest(url = "https://example.com/api", method = "GET")
        val expectedResponse = HttpResponse(code = 200, body = "OK")
        val delegate = mock<HttpClient>()
        runBlocking { whenever(delegate.execute(request)).thenReturn(expectedResponse) }

        val scriptClient = ScriptHttpClient(delegate)
        val latch = CountDownLatch(1)
        val resultRef = AtomicReference<HttpResponse?>()

        val bgThread = Thread({
            resultRef.set(scriptClient.executeSync(request))
            latch.countDown()
        }, "ScriptHttpClientTest-bg")

        bgThread.isDaemon = true
        bgThread.start()

        val completed = latch.await(10, TimeUnit.SECONDS)
        assert(completed) {
            "executeSync deadlocked — did not complete within 10s when called from a background thread"
        }
        val result = resultRef.get()
        assertEquals(200, result?.code)
        assertEquals("OK", result?.body)
    }

    @Test
    fun `executeSync propagates the exact HttpResponse instance from the delegate`() = runBlocking {
        val request = HttpRequest(url = "https://example.com", method = "POST")
        val expectedResponse = HttpResponse(code = 201, body = "created")
        val delegate = mock<HttpClient>()
        whenever(delegate.execute(request)).thenReturn(expectedResponse)

        val response = ScriptHttpClient(delegate).executeSync(request)
        assertSame(expectedResponse, response)
    }
}
