package com.itangcent.easyapi.http

import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.context.RuleContext
import com.itangcent.easyapi.rule.engine.RuleEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.util.concurrent.atomic.AtomicLong

class HttpClientScriptInterceptorTest {

    private lateinit var delegate: HttpClient
    private lateinit var interceptor: HttpClientScriptInterceptor

    @Before
    fun setUp() {
        delegate = mock()
        interceptor = HttpClientScriptInterceptor(delegate, null)
    }

    @Test
    fun testExecuteDelegatesToUnderlyingClient() = runBlocking {
        val request = HttpRequest(url = "https://example.com/api", method = "GET")
        val expectedResponse = HttpResponse(code = 200, body = "OK")
        whenever(delegate.execute(request)).thenReturn(expectedResponse)

        val response = interceptor.execute(request)
        assertEquals("Should return delegate response code", 200, response.code)
        assertEquals("Should return delegate response body", "OK", response.body)
    }

    @Test
    fun testCloseDelegatesToUnderlyingClient() {
        interceptor.close()
        verify(delegate).close()
    }

    @Test
    fun testExecuteWithNullRuleEngine() = runBlocking {
        val request = HttpRequest(url = "https://example.com/api", method = "POST", body = "data")
        val expectedResponse = HttpResponse(code = 201, body = "Created")
        whenever(delegate.execute(request)).thenReturn(expectedResponse)

        val response = interceptor.execute(request)
        assertEquals("Should return delegate response", 201, response.code)
    }

    @Test
    fun testHttpRequestWrapper() {
        val request = HttpRequest(
            url = "https://example.com/api",
            method = "POST",
            headers = listOf(kv("Content-Type", "application/json")),
            query = listOf(kv("page", "1")),
            body = """{"key":"value"}""",
            formParams = listOf(FormParam.Text("field", "val")),
            cookies = listOf(HttpCookie("session", "abc")),
            contentType = "application/json"
        )
        val wrapper = HttpRequestWrapper(request)

        assertEquals("https://example.com/api", wrapper.url())
        assertEquals("POST", wrapper.method())
        assertEquals(1, wrapper.headers().size)
        assertEquals(1, wrapper.query().size)
        assertEquals("""{"key":"value"}""", wrapper.body())
        assertEquals(1, wrapper.formParams().size)
        assertEquals(1, wrapper.cookies().size)
        assertEquals("application/json", wrapper.contentType())
    }

    @Test
    fun testHttpResponseWrapper() {
        val request = HttpRequest(url = "https://example.com/api", method = "GET")
        val response = HttpResponse(
            code = 200,
            headers = mapOf("Content-Type" to listOf("application/json")),
            body = "OK"
        )
        val requestWrapper = HttpRequestWrapper(request)
        val wrapper = HttpResponseWrapper(response, requestWrapper)

        assertEquals(200, wrapper.code())
        assertEquals(1, wrapper.headers().size)
        assertEquals("OK", wrapper.body())
        assertSame(requestWrapper, wrapper.request())
    }

    @Test
    fun testHttpResponseWrapperDiscard() {
        val request = HttpRequest(url = "https://example.com/api", method = "GET")
        val response = HttpResponse(code = 200)
        val requestWrapper = HttpRequestWrapper(request)
        val wrapper = HttpResponseWrapper(response, requestWrapper)

        assertFalse("Should not be discarded initially", wrapper.isDiscarded())
        wrapper.discard()
        assertTrue("Should be discarded after discard()", wrapper.isDiscarded())
    }

    @Test
    fun testInterceptorWithRuleEngine() = runBlocking {
        val ruleEngine = mock<RuleEngine>()

        val interceptorWithEngine = HttpClientScriptInterceptor(delegate, ruleEngine)
        val request = HttpRequest(url = "https://example.com/api", method = "GET")
        val expectedResponse = HttpResponse(code = 200, body = "OK")
        whenever(delegate.execute(request)).thenReturn(expectedResponse)

        val response = interceptorWithEngine.execute(request)
        assertEquals(200, response.code)
    }

    // ---- T2.2: mutation + recursion guard (Spec: ai-workflow-patterns, D2/D3) ----

    /**
     * Minimal [RuleContext] double that records `setExt` calls into a map and
     * serves them back from `getExt`. Lets the test simulate a script reading the
     * `request`/`response` ext objects that the interceptor publishes via its
     * `contextHandle` lambda.
     */
    private fun recordingContext(): RuleContext {
        val exts = mutableMapOf<String, Any?>()
        val ctx = mock<RuleContext>()
        doAnswer { inv ->
            exts[inv.getArgument<String>(0)] = inv.getArgument<Any?>(1)
            Unit
        }.whenever(ctx).setExt(any(), org.mockito.ArgumentMatchers.any())
        doAnswer { inv -> exts[inv.getArgument<String>(0)] }
            .whenever(ctx).getExt(any())
        return ctx
    }

    @Test
    fun `http call after script mutates request header and discards response - retry carries new header`() = runBlocking {
        // delegate records every HttpRequest it receives.
        val receivedRequests = mutableListOf<HttpRequest>()
        val delegate = mock<HttpClient>()
        doAnswer { inv ->
            receivedRequests.add(inv.getArgument<HttpRequest>(0))
            HttpResponse(code = 200, body = "OK")
        }.whenever(delegate).execute(any())

        // Simulate a 401-refresh script: on the FIRST http.call.after, set a new
        // Authorization header on the wrapper and discard the response (trigger retry).
        val afterCallCount = AtomicLong(0)
        val ruleEngine = mock<RuleEngine>()
        doAnswer { inv ->
            val contextHandle = inv.getArgument<(RuleContext) -> Unit>(1)
            val ctx = recordingContext()
            contextHandle(ctx)
            if (afterCallCount.incrementAndGet() == 1L) {
                val req = ctx.getExt("request") as HttpRequestWrapper
                val resp = ctx.getExt("response") as HttpResponseWrapper
                req.setHeader("Authorization", "Bearer newToken")
                resp.discard()
            }
        }.whenever(ruleEngine).evaluate(eq(RuleKeys.HTTP_CALL_AFTER), any<(RuleContext) -> Unit>())

        val interceptor = HttpClientScriptInterceptor(delegate, ruleEngine)
        val response = interceptor.execute(
            HttpRequest(url = "https://api.example.com/secure", method = "GET")
        )

        assertEquals(200, response.code)
        assertEquals("expected 2 attempts (original + retry after discard)", 2, receivedRequests.size)
        assertTrue(
            "first attempt must NOT carry Authorization",
            receivedRequests[0].headers.none { it.name.equals("Authorization", ignoreCase = true) }
        )
        val authHeader = receivedRequests[1].headers.first { it.name.equals("Authorization", ignoreCase = true) }
        assertEquals("Bearer newToken", authHeader.value)
    }

    @Test
    fun `recursion guard - depth 0 and 1 run hooks, depth 2 and beyond skip hooks`() = runBlocking {
        val delegate = mock<HttpClient>()
        whenever(delegate.execute(any())).thenReturn(HttpResponse(code = 200, body = "OK"))

        val evaluateCount = AtomicLong(0)
        val reentrySafetyValve = AtomicLong(0)
        val subReq = HttpRequest(url = "https://example.com/refresh", method = "POST")
        val ruleEngine = mock<RuleEngine>()
        val interceptor = HttpClientScriptInterceptor(delegate, ruleEngine)

        // Every evaluate() call (BEFORE or AFTER) increments the counter. On AFTER,
        // simulate a script-spawned sub-request (re-enter execute on the same thread,
        // same interceptor — mirroring ScriptHttpClient.executeSync → delegate.execute).
        // Capped at 2 re-entries so a broken depth guard fails the assertion instead
        // of StackOverflow-ing.
        doAnswer { inv ->
            val key = inv.getArgument<RuleKey.EventKey>(0)
            val contextHandle = inv.getArgument<(RuleContext) -> Unit>(1)
            contextHandle(recordingContext())
            evaluateCount.incrementAndGet()
            if (key == RuleKeys.HTTP_CALL_AFTER && reentrySafetyValve.incrementAndGet() <= 2) {
                runBlocking { interceptor.execute(subReq) }
            }
        }.whenever(ruleEngine).evaluate(any<RuleKey.EventKey>(), any<(RuleContext) -> Unit>())

        interceptor.execute(HttpRequest(url = "https://example.com/api", method = "GET"))

        // d=0 (top-level):  BEFORE(1) + AFTER(2)   → AFTER re-enters → d=1
        // d=1 (sub-request): BEFORE(3) + AFTER(4)   → AFTER re-enters → d=2
        // d=2 (sub-sub):     hooks SKIPPED (d>=2)   → 0 calls, no further re-entry
        assertEquals(
            "depth 0 + depth 1 run hooks (2+2=4); depth >= 2 skips hooks (0). Expected 4 evaluate calls.",
            4L,
            evaluateCount.get()
        )
    }
}
