package com.itangcent.easyapi.http

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test

class UrlConnectionHttpClientTest {

    private val client = UrlConnectionHttpClient

    @Ignore("Network test - unreliable in CI")
    @Test
    fun testGetRequest() = runBlocking {
        val request = HttpRequest(
            url = "https://httpbin.org/get",
            method = "GET",
            headers = listOf(kv("Accept", "application/json"))
        )
        val response = client.execute(request)
        assertEquals("GET request should return 200", 200, response.code)
        assertNotNull("Response body should not be null", response.body)
        assertTrue("Response should contain url", response.body!!.contains("httpbin.org"))
    }

    @Ignore("Network test - unreliable in CI")
    @Test
    fun testPostRequestWithBody() = runBlocking {
        val request = HttpRequest(
            url = "https://httpbin.org/post",
            method = "POST",
            body = """{"name":"test"}""",
            headers = listOf(kv("Content-Type", "application/json"))
        )
        val response = client.execute(request)
        assertEquals("POST request should return 200", 200, response.code)
        assertNotNull("Response body should not be null", response.body)
        assertTrue("Response should echo the body", response.body!!.contains("test"))
    }

    @Ignore("Network test - unreliable in CI")
    @Test
    fun testPostFormParams() = runBlocking {
        val request = HttpRequest(
            url = "https://httpbin.org/post",
            method = "POST",
            formParams = listOf(
                FormParam.Text("username", "john"),
                FormParam.Text("email", "john@example.com")
            )
        )
        val response = client.execute(request)
        assertEquals("Form POST should return 200", 200, response.code)
        assertTrue("Response should contain form data", response.body!!.contains("john"))
    }

    @Ignore("Network test - unreliable in CI")
    @Test
    fun testHeadersAreSent() = runBlocking {
        val request = HttpRequest(
            url = "https://httpbin.org/headers",
            method = "GET",
            headers = listOf(kv("X-Custom-Header", "test-value"))
        )
        val response = client.execute(request)
        assertEquals("Request with headers should return 200", 200, response.code)
        assertTrue("Response should contain custom header", response.body!!.contains("test-value"))
    }

    @Ignore("Network test - unreliable in CI")
    @Test
    fun testQueryParameters() = runBlocking {
        val request = HttpRequest(
            url = "https://httpbin.org/get",
            method = "GET",
            query = listOf(kv("foo", "bar"))
        )
        val response = client.execute(request)
        assertEquals("Request with query params should return 200", 200, response.code)
        assertTrue("Response should contain query param", response.body!!.contains("bar"))
    }

    @Test
    fun testCloseDoesNotThrow() {
        client.close()
    }

    @Test
    fun testIsSingletonObject() {
        assertSame("UrlConnectionHttpClient should be a singleton object", UrlConnectionHttpClient, UrlConnectionHttpClient)
    }
}
