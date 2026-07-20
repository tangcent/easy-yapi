package com.itangcent.easyapi.core.http

import org.junit.Assert.*
import org.junit.Test

class HttpRequestWrapperTest {

    @Test
    fun testUrl() {
        val request = HttpRequest(
            url = "http://localhost:8080/api/users",
            method = "GET"
        )
        val wrapper = HttpRequestWrapper(request)
        
        assertEquals("http://localhost:8080/api/users", wrapper.url())
    }

    @Test
    fun testMethod() {
        val request = HttpRequest(
            url = "http://test.com",
            method = "POST"
        )
        val wrapper = HttpRequestWrapper(request)
        
        assertEquals("POST", wrapper.method())
    }

    @Test
    fun testHeaders() {
        val request = HttpRequest(
            url = "http://test.com",
            method = "GET",
            headers = listOf(KeyValue("Authorization", "Bearer token"))
        )
        val wrapper = HttpRequestWrapper(request)
        
        assertEquals(1, wrapper.headers().size)
        assertEquals("Authorization", wrapper.headers()[0].name)
        assertEquals("Bearer token", wrapper.headers()[0].value)
    }

    @Test
    fun testQuery() {
        val request = HttpRequest(
            url = "http://test.com",
            method = "GET",
            query = listOf(KeyValue("id", "123"))
        )
        val wrapper = HttpRequestWrapper(request)
        
        assertEquals(1, wrapper.query().size)
        assertEquals("id", wrapper.query()[0].name)
    }

    @Test
    fun testBody() {
        val request = HttpRequest(
            url = "http://test.com",
            method = "POST",
            body = "{\"name\":\"test\"}"
        )
        val wrapper = HttpRequestWrapper(request)
        
        assertEquals("{\"name\":\"test\"}", wrapper.body())
    }

    @Test
    fun testFormParams() {
        val request = HttpRequest(
            url = "http://test.com",
            method = "POST",
            formParams = listOf(FormParam.Text("username", "admin"))
        )
        val wrapper = HttpRequestWrapper(request)
        
        assertEquals(1, wrapper.formParams().size)
        assertEquals("username", wrapper.formParams()[0].name)
    }

    @Test
    fun testCookies() {
        val request = HttpRequest(
            url = "http://test.com",
            method = "GET",
            cookies = listOf(HttpCookie("session", "abc123"))
        )
        val wrapper = HttpRequestWrapper(request)
        
        assertEquals(1, wrapper.cookies().size)
        assertEquals("session", wrapper.cookies()[0].name)
    }

    @Test
    fun testContentType() {
        val request = HttpRequest(
            url = "http://test.com",
            method = "POST",
            contentType = "application/json"
        )
        val wrapper = HttpRequestWrapper(request)

        assertEquals("application/json", wrapper.contentType())
    }

    // ---- T2.2: mutable header support (Spec: ai-workflow-patterns, D2) ----

    @Test
    fun testSetHeaderAddsNewHeader() {
        val request = HttpRequest(url = "http://test.com", method = "GET")
        val wrapper = HttpRequestWrapper(request)

        assertEquals(0, wrapper.headers().size)
        wrapper.setHeader("Authorization", "Bearer token")
        assertEquals(1, wrapper.headers().size)
        assertEquals("Authorization", wrapper.headers()[0].name)
        assertEquals("Bearer token", wrapper.headers()[0].value)
    }

    @Test
    fun testSetHeaderUpsertsCaseInsensitively() {
        val request = HttpRequest(
            url = "http://test.com",
            method = "GET",
            headers = listOf(KeyValue("Content-Type", "application/json"))
        )
        val wrapper = HttpRequestWrapper(request)

        assertEquals(1, wrapper.headers().size)
        // Replace existing header (case-insensitive match)
        wrapper.setHeader("content-type", "text/plain")
        assertEquals("upsert should NOT add a duplicate header", 1, wrapper.headers().size)
        assertEquals("text/plain", wrapper.headers()[0].value)
    }

    @Test
    fun testSetHeaderReplacesAllMatchingCaseVariants() {
        val request = HttpRequest(
            url = "http://test.com",
            method = "GET",
            headers = listOf(KeyValue("X-Custom", "old"), KeyValue("x-custom", "dup"))
        )
        val wrapper = HttpRequestWrapper(request)

        wrapper.setHeader("X-CUSTOM", "new")
        assertEquals(1, wrapper.headers().size)
        assertEquals("new", wrapper.headers()[0].value)
    }

    @Test
    fun testRemoveHeaderIsCaseInsensitive() {
        val request = HttpRequest(
            url = "http://test.com",
            method = "GET",
            headers = listOf(KeyValue("Authorization", "Bearer x"), KeyValue("Content-Type", "application/json"))
        )
        val wrapper = HttpRequestWrapper(request)

        wrapper.removeHeader("authorization")
        assertEquals(1, wrapper.headers().size)
        assertEquals("Content-Type", wrapper.headers()[0].name)
    }

    @Test
    fun testRemoveHeaderNoOpWhenAbsent() {
        val request = HttpRequest(
            url = "http://test.com",
            method = "GET",
            headers = listOf(KeyValue("Content-Type", "application/json"))
        )
        val wrapper = HttpRequestWrapper(request)

        wrapper.removeHeader("Authorization")
        assertEquals(1, wrapper.headers().size)
    }

    @Test
    fun testToHttpRequestCopiesAllFieldsAndUsesHeaderOverrides() {
        val request = HttpRequest(
            url = "http://test.com/api",
            method = "POST",
            headers = listOf(KeyValue("Content-Type", "application/json")),
            query = listOf(KeyValue("page", "1")),
            body = """{"k":"v"}""",
            formParams = listOf(FormParam.Text("field", "val")),
            cookies = listOf(HttpCookie("session", "abc")),
            contentType = "application/json"
        )
        val wrapper = HttpRequestWrapper(request)

        wrapper.setHeader("Authorization", "Bearer newTok")
        wrapper.removeHeader("Content-Type")

        val built = wrapper.toHttpRequest()
        assertEquals("http://test.com/api", built.url)
        assertEquals("POST", built.method)
        assertEquals(1, built.headers.size)
        assertEquals("Authorization", built.headers[0].name)
        assertEquals("Bearer newTok", built.headers[0].value)
        assertEquals(1, built.query.size)
        assertEquals("""{"k":"v"}""", built.body)
        assertEquals(1, built.formParams.size)
        assertEquals(1, built.cookies.size)
        assertEquals("application/json", built.contentType)
    }

    @Test
    fun testToHttpRequestPreservesOriginalWhenNoMutation() {
        val request = HttpRequest(
            url = "http://test.com",
            method = "GET",
            headers = listOf(KeyValue("Authorization", "Bearer original"))
        )
        val wrapper = HttpRequestWrapper(request)

        val built = wrapper.toHttpRequest()
        assertEquals(request.headers, built.headers)
        assertEquals(request.url, built.url)
        assertEquals(request.method, built.method)
    }
}

class HttpResponseWrapperTest {

    @Test
    fun testCode() {
        val request = HttpRequest(url = "http://test.com", method = "GET")
        val response = HttpResponse(code = 200, body = "OK")
        val requestWrapper = HttpRequestWrapper(request)
        val wrapper = HttpResponseWrapper(response, requestWrapper)
        
        assertEquals(200, wrapper.code())
    }

    @Test
    fun testHeaders() {
        val request = HttpRequest(url = "http://test.com", method = "GET")
        val response = HttpResponse(
            code = 200,
            body = "OK",
            headers = mapOf("Content-Type" to listOf("application/json"))
        )
        val requestWrapper = HttpRequestWrapper(request)
        val wrapper = HttpResponseWrapper(response, requestWrapper)
        
        assertEquals(1, wrapper.headers().size)
        assertTrue(wrapper.headers().containsKey("Content-Type"))
    }

    @Test
    fun testBody() {
        val request = HttpRequest(url = "http://test.com", method = "GET")
        val response = HttpResponse(code = 200, body = "{\"result\":\"success\"}")
        val requestWrapper = HttpRequestWrapper(request)
        val wrapper = HttpResponseWrapper(response, requestWrapper)
        
        assertEquals("{\"result\":\"success\"}", wrapper.body())
    }

    @Test
    fun testRequest() {
        val request = HttpRequest(url = "http://test.com", method = "GET")
        val response = HttpResponse(code = 200, body = "OK")
        val requestWrapper = HttpRequestWrapper(request)
        val wrapper = HttpResponseWrapper(response, requestWrapper)
        
        assertEquals(requestWrapper, wrapper.request())
    }

    @Test
    fun testDiscard() {
        val request = HttpRequest(url = "http://test.com", method = "GET")
        val response = HttpResponse(code = 200, body = "OK")
        val requestWrapper = HttpRequestWrapper(request)
        val wrapper = HttpResponseWrapper(response, requestWrapper)
        
        assertFalse(wrapper.isDiscarded())
        wrapper.discard()
        assertTrue(wrapper.isDiscarded())
    }
}
