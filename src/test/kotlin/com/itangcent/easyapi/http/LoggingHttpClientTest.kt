package com.itangcent.easyapi.http

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LoggingHttpClientTest {

    @Test
    fun `test logging extension creates LoggingHttpClient instance`() {
        val mockDelegate = MockHttpClient()
        val logged = mockDelegate.logging()

        assertTrue("logging() should return a LoggingHttpClient", logged is LoggingHttpClient)
    }

    @Test
    fun `test successful request delegates and returns response`() = runBlocking {
        val expectedResponse = HttpResponse(code = 200, body = """{"status":"ok"}""")
        val mockDelegate = MockHttpClient(response = expectedResponse)
        val loggingClient = LoggingHttpClient(mockDelegate)

        val request = HttpRequest(url = "http://localhost/api/test", method = "GET")
        val response = loggingClient.execute(request)

        assertNotNull("Response should not be null", response)
        assertEquals(200, response.code)
        assertEquals("""{"status":"ok"}""", response.body)
        assertTrue("Delegate should have been called", mockDelegate.wasCalled)
        assertEquals(request, mockDelegate.lastRequest)
    }

    @Test
    fun `test exception is re-thrown after logging`() = runBlocking {
        val expectedException = RuntimeException("Connection refused")
        val mockDelegate = MockHttpClient(exception = expectedException)
        val loggingClient = LoggingHttpClient(mockDelegate)

        val request = HttpRequest(url = "http://localhost/api/fail", method = "POST")

        try {
            loggingClient.execute(request)
            fail("Expected RuntimeException to be thrown")
        } catch (e: RuntimeException) {
            assertEquals("Connection refused", e.message)
            assertTrue("Delegate should have been called even on exception", mockDelegate.wasCalled)
        }
    }

    @Test
    fun `test logging wraps different HTTP methods`() = runBlocking {
        val methods = listOf("GET", "POST", "PUT", "DELETE", "PATCH")

        for (method in methods) {
            val mockDelegate = MockHttpClient(response = HttpResponse(code = 200))
            val loggingClient = LoggingHttpClient(mockDelegate)

            val request = HttpRequest(url = "http://localhost/api/resource", method = method)
            val response = loggingClient.execute(request)

            assertEquals("Method $method should return 200", 200, response.code)
            assertEquals("Delegate method should match request", method, mockDelegate.lastRequest?.method)
        }
    }

    @Test
    fun `test logging preserves response headers and body`() = runBlocking {
        val expectedResponse = HttpResponse(
            code = 201,
            headers = mapOf("Content-Type" to listOf("application/json"), "X-Custom" to listOf("value")),
            body = """{"id":42,"name":"created"}"""
        )
        val mockDelegate = MockHttpClient(response = expectedResponse)
        val loggingClient = LoggingHttpClient(mockDelegate)

        val response = loggingClient.execute(HttpRequest(url = "http://localhost/api/create", method = "POST"))

        assertEquals(201, response.code)
        assertEquals(2, response.headers.size)
        assertEquals(listOf("application/json"), response.headers["Content-Type"])
        assertEquals("""{"id":42,"name":"created"}""", response.body)
    }

    @Test
    fun `test logging with query parameters in URL`() = runBlocking {
        val mockDelegate = MockHttpClient(response = HttpResponse(code = 200, body = "ok"))
        val loggingClient = LoggingHttpClient(mockDelegate)

        val request = HttpRequest(
            url = "http://localhost/api/search",
            method = "GET",
            query = listOf(kv("q", "test"), kv("page", "1"))
        )

        val response = loggingClient.execute(request)

        assertEquals(200, response.code)
        assertNotNull(mockDelegate.lastRequest)
        assertEquals(2, mockDelegate.lastRequest!!.query.size)
    }

    @Test
    fun `test close delegates to underlying client`() {
        val mockDelegate = MockHttpClient()
        val loggingClient = LoggingHttpClient(mockDelegate)

        assertFalse("Should not be closed initially", mockDelegate.isClosed)
        loggingClient.close()
        assertTrue("Close should delegate to underlying client", mockDelegate.isClosed)
    }

    private class MockHttpClient(
        private val response: HttpResponse? = null,
        private val exception: Exception? = null
    ) : HttpClient {

        var wasCalled = false
            private set
        var lastRequest: HttpRequest? = null
            private set
        var isClosed = false
            private set

        override suspend fun execute(request: HttpRequest): HttpResponse {
            wasCalled = true
            lastRequest = request
            exception?.let { throw it }
            return response!!
        }

        override fun close() {
            isClosed = true
        }
    }
}
