package com.itangcent.easyapi.http

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ApacheHttpClientTest {

    private lateinit var client: ApacheHttpClient

    @Before
    fun setUp() {
        client = ApacheHttpClient(timeoutMs = 5000, unsafeSsl = true)
    }

    @After
    fun tearDown() {
        client.close()
    }

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

    @Test
    fun testPostFormParams() = runBlocking {
        val request = HttpRequest(
            url = "https://httpbin.org/post",
            method = "POST",
            formParams = listOf(
                FormParam.Text("username", "john"),
                FormParam.Text("password", "secret")
            )
        )
        val response = client.execute(request)
        assertEquals("Form POST should return 200", 200, response.code)
        assertNotNull("Response body should not be null", response.body)
        assertTrue("Response should contain form data", response.body!!.contains("john"))
    }

    @Test
    fun testInvalidUrlThrowsException() {
        val request = HttpRequest(
            url = "not-a-valid-url",
            method = "GET"
        )
        try {
            runBlocking { client.execute(request) }
            fail("Should throw exception for invalid URL")
        } catch (e: Exception) {
            assertTrue("Should throw for invalid URL", true)
        }
    }

    @Test
    fun testCloseDoesNotThrow() {
        val localClient = ApacheHttpClient(timeoutMs = 5000)
        localClient.close()
    }

    @Test
    fun testDoubleCloseDoesNotThrow() {
        val localClient = ApacheHttpClient(timeoutMs = 5000)
        localClient.close()
        localClient.close()
    }

    @Test
    fun testClientWithUnsafeSsl() {
        val unsafeClient = ApacheHttpClient(timeoutMs = 5000, unsafeSsl = true)
        assertNotNull("Client with unsafe SSL should be created", unsafeClient)
        unsafeClient.close()
    }

    @Test
    fun testClientWithoutUnsafeSsl() {
        val safeClient = ApacheHttpClient(timeoutMs = 5000, unsafeSsl = false)
        assertNotNull("Client without unsafe SSL should be created", safeClient)
        safeClient.close()
    }

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
}
