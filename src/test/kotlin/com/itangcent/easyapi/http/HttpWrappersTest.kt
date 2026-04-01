package com.itangcent.easyapi.http

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
