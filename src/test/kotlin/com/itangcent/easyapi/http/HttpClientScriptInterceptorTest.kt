package com.itangcent.easyapi.http

import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.rule.context.RuleContext
import com.itangcent.easyapi.rule.engine.RuleEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

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
}
