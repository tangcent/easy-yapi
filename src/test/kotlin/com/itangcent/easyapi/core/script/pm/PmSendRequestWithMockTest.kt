package com.itangcent.easyapi.core.script.pm

import com.itangcent.easyapi.core.http.HttpClient
import com.itangcent.easyapi.core.http.HttpRequest
import com.itangcent.easyapi.core.http.HttpResponse
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for PmSendRequest with a mock HttpClient.
 * Covers both URL-based and options-map-based request invocation.
 */
class PmSendRequestWithMockTest {

    private lateinit var mockClient: MockPmHttpClient
    private lateinit var pmSendRequest: PmSendRequest

    @Before
    fun setUp() {
        mockClient = MockPmHttpClient()
        pmSendRequest = PmSendRequest(mockClient)
    }

    private fun createCallback(block: (PmResponse) -> Unit): PmSendRequestCallback {
        val callback = PmSendRequestCallback()
        callback.setHandler(block)
        return callback
    }

    // ==================== URL-based invoke ====================

    @Test
    fun `invoke with URL sends GET request`() {
        mockClient.nextResponse = HttpResponse(code = 200, body = """{"status":"ok"}""")
        var receivedCode = 0
        var receivedBody = ""
        pmSendRequest.invoke("https://api.example.com/test", createCallback { response ->
            receivedCode = response.code
            receivedBody = response.text()
        })
        assertEquals(200, receivedCode)
        assertEquals("""{"status":"ok"}""", receivedBody)
        assertEquals("GET", mockClient.lastRequest?.method)
        assertEquals("https://api.example.com/test", mockClient.lastRequest?.url)
    }

    @Test
    fun `invoke with URL handles error response`() {
        mockClient.nextResponse = HttpResponse(code = 500, body = "Internal Server Error")
        var receivedCode = 0
        var receivedBody = ""
        pmSendRequest.invoke("https://api.example.com/error", createCallback { response ->
            receivedCode = response.code
            receivedBody = response.text()
        })
        assertEquals(500, receivedCode)
        assertEquals("Internal Server Error", receivedBody)
    }

    @Test
    fun `invoke with URL handles exception`() {
        mockClient.shouldThrow = true
        var receivedCode = -1
        var receivedStatus = ""
        pmSendRequest.invoke("https://api.example.com/fail", createCallback { response ->
            receivedCode = response.code
            receivedStatus = response.status
        })
        assertEquals(0, receivedCode)
        assertEquals("Error", receivedStatus)
    }

    @Test
    fun `invoke with null httpClient is no-op`() {
        val noOpRequest = PmSendRequest(null)
        var callbackInvoked = false
        noOpRequest.invoke("https://api.example.com/test", createCallback { callbackInvoked = true })
        assertFalse(callbackInvoked)
    }

    @Test
    fun `invoke with URL captures response size`() {
        val body = "Hello World"
        mockClient.nextResponse = HttpResponse(code = 200, body = body)
        var receivedSize = 0L
        pmSendRequest.invoke("https://api.example.com/test", createCallback { response ->
            receivedSize = response.responseSize
        })
        assertEquals(body.length.toLong(), receivedSize)
    }

    @Test
    fun `invoke with URL captures response headers`() {
        mockClient.nextResponse = HttpResponse(
            code = 200,
            headers = mapOf("Content-Type" to listOf("application/json"), "X-Custom" to listOf("value")),
            body = "{}"
        )
        var hasContentType = false
        pmSendRequest.invoke("https://api.example.com/test", createCallback { response ->
            hasContentType = response.headers.has("Content-Type")
        })
        assertTrue(hasContentType)
    }

    @Test
    fun `invoke with URL and null body`() {
        mockClient.nextResponse = HttpResponse(code = 204, body = null)
        var receivedCode = 0
        var receivedBody = ""
        var receivedSize = 0L
        pmSendRequest.invoke("https://api.example.com/test", createCallback { response ->
            receivedCode = response.code
            receivedBody = response.text()
            receivedSize = response.responseSize
        })
        assertEquals(204, receivedCode)
        assertEquals("", receivedBody)
        assertEquals(0L, receivedSize)
    }

    // ==================== Options-map-based invoke ====================

    @Test
    fun `invoke with options map sends POST request`() {
        mockClient.nextResponse = HttpResponse(code = 201, body = """{"id":1}""")
        var receivedCode = 0
        pmSendRequest.invoke(
            mapOf(
                "url" to "https://api.example.com/data",
                "method" to "POST",
                "header" to listOf(mapOf("key" to "Content-Type", "value" to "application/json")),
                "body" to mapOf("raw" to """{"key":"value"}""")
            ),
            createCallback { response ->
                receivedCode = response.code
            }
        )
        assertEquals(201, receivedCode)
        assertEquals("POST", mockClient.lastRequest?.method)
        assertEquals("https://api.example.com/data", mockClient.lastRequest?.url)
        assertEquals("""{"key":"value"}""", mockClient.lastRequest?.body)
        assertTrue(mockClient.lastRequest?.headers?.any { it.first == "Content-Type" } == true)
    }

    @Test
    fun `invoke with options map defaults to GET`() {
        mockClient.nextResponse = HttpResponse(code = 200, body = "ok")
        pmSendRequest.invoke(mapOf("url" to "https://api.example.com/test"), createCallback { _ -> })
        assertEquals("GET", mockClient.lastRequest?.method)
    }

    @Test
    fun `invoke with options map without URL is no-op`() {
        var callbackInvoked = false
        pmSendRequest.invoke(mapOf("method" to "GET"), createCallback { callbackInvoked = true })
        assertFalse(callbackInvoked)
        assertNull(mockClient.lastRequest)
    }

    @Test
    fun `invoke with options map handles exception`() {
        mockClient.shouldThrow = true
        var receivedCode = -1
        var receivedStatus = ""
        pmSendRequest.invoke(
            mapOf("url" to "https://api.example.com/fail", "method" to "GET"),
            createCallback { response ->
                receivedCode = response.code
                receivedStatus = response.status
            }
        )
        assertEquals(0, receivedCode)
        assertEquals("Error", receivedStatus)
    }

    @Test
    fun `invoke with options map and null httpClient is no-op`() {
        val noOpRequest = PmSendRequest(null)
        var callbackInvoked = false
        noOpRequest.invoke(mapOf("url" to "https://api.example.com/test"), createCallback { callbackInvoked = true })
        assertFalse(callbackInvoked)
    }

    @Test
    fun `invoke with options map without headers`() {
        mockClient.nextResponse = HttpResponse(code = 200, body = "ok")
        pmSendRequest.invoke(
            mapOf("url" to "https://api.example.com/test", "method" to "GET"),
            createCallback { _ -> }
        )
        assertTrue(mockClient.lastRequest?.headers?.isEmpty() == true)
    }

    @Test
    fun `invoke with options map without body`() {
        mockClient.nextResponse = HttpResponse(code = 200, body = "ok")
        pmSendRequest.invoke(
            mapOf("url" to "https://api.example.com/test", "method" to "GET"),
            createCallback { _ -> }
        )
        assertNull(mockClient.lastRequest?.body)
    }

    // ==================== call() aliases ====================

    @Test
    fun `call with URL delegates to invoke`() {
        mockClient.nextResponse = HttpResponse(code = 200, body = "ok")
        var receivedCode = 0
        pmSendRequest.call("https://api.example.com/test", createCallback { response ->
            receivedCode = response.code
        })
        assertEquals(200, receivedCode)
    }

    @Test
    fun `call with options map delegates to invoke`() {
        mockClient.nextResponse = HttpResponse(code = 200, body = "ok")
        var receivedCode = 0
        pmSendRequest.call(mapOf("url" to "https://api.example.com/test"), createCallback { response ->
            receivedCode = response.code
        })
        assertEquals(200, receivedCode)
    }

    // ==================== PmSendRequestCallback ====================

    @Test
    fun `PmSendRequestCallback invokes handler`() {
        val callback = PmSendRequestCallback()
        var receivedCode = 0
        callback.setHandler { response -> receivedCode = response.code }
        val testResponse = PmResponse(code = 200, status = "OK", headers = PmHeaderList(), responseTime = 0, responseSize = 0, rawBody = "test")
        callback.call(testResponse)
        assertEquals(200, receivedCode)
    }

    @Test
    fun `PmSendRequestCallback without handler does not crash`() {
        val callback = PmSendRequestCallback()
        val testResponse = PmResponse(code = 200, status = "OK", headers = PmHeaderList(), responseTime = 0, responseSize = 0, rawBody = "test")
        callback.call(testResponse)
    }

    // ==================== PmResponse ====================

    @Test
    fun `PmResponse text returns rawBody`() {
        val response = PmResponse(code = 200, status = "OK", headers = PmHeaderList(), responseTime = 0, responseSize = 0, rawBody = "test body")
        assertEquals("test body", response.text())
    }

    @Test
    fun `PmResponse json parses valid JSON`() {
        val response = PmResponse(code = 200, status = "OK", headers = PmHeaderList(), responseTime = 0, responseSize = 0, rawBody = """{"name":"Alice"}""")
        assertNotNull(response.json())
    }

    @Test
    fun `PmResponse json returns null for invalid JSON`() {
        val response = PmResponse(code = 200, status = "OK", headers = PmHeaderList(), responseTime = 0, responseSize = 0, rawBody = "not json")
        assertNull(response.json())
    }

    // ==================== PmHeaderList ====================

    @Test
    fun `PmHeaderList add and get`() {
        val headers = PmHeaderList()
        headers.add("Content-Type", "application/json")
        assertEquals("application/json", headers.get("Content-Type"))
    }

    @Test
    fun `PmHeaderList get is case-insensitive`() {
        val headers = PmHeaderList()
        headers.add("Content-Type", "application/json")
        assertEquals("application/json", headers.get("content-type"))
    }

    @Test
    fun `PmHeaderList upsert replaces existing`() {
        val headers = PmHeaderList()
        headers.add("Content-Type", "text/plain")
        headers.upsert("Content-Type", "application/json")
        assertEquals("application/json", headers.get("Content-Type"))
    }

    @Test
    fun `PmHeaderList upsert adds new when not found`() {
        val headers = PmHeaderList()
        headers.upsert("Accept", "application/json")
        assertEquals("application/json", headers.get("Accept"))
    }

    @Test
    fun `PmHeaderList remove`() {
        val headers = PmHeaderList()
        headers.add("X-Custom", "value")
        headers.remove("X-Custom")
        assertNull(headers.get("X-Custom"))
    }

    @Test
    fun `PmHeaderList has`() {
        val headers = PmHeaderList()
        headers.add("Content-Type", "application/json")
        assertTrue(headers.has("Content-Type"))
        assertFalse(headers.has("Authorization"))
    }

    @Test
    fun `PmHeaderList all returns list of maps`() {
        val headers = PmHeaderList()
        headers.add("Content-Type", "application/json")
        headers.add("Accept", "text/html")
        val all = headers.all()
        assertEquals(2, all.size)
        assertEquals("Content-Type", all[0]["key"])
        assertEquals("application/json", all[0]["value"])
    }

    @Test
    fun `PmHeaderList add from map`() {
        val headers = PmHeaderList()
        headers.add(mapOf("key" to "X-Custom", "value" to "test"))
        assertEquals("test", headers.get("X-Custom"))
    }

    @Test
    fun `PmHeaderList initialized with pairs`() {
        val headers = PmHeaderList(listOf("Content-Type" to "application/json", "Accept" to "text/html"))
        assertEquals("application/json", headers.get("Content-Type"))
        assertEquals("text/html", headers.get("Accept"))
    }
}

/**
 * Simple mock HttpClient that returns a pre-configured response.
 */
class MockPmHttpClient : HttpClient {
    var nextResponse: HttpResponse = HttpResponse(code = 200, body = "")
    var lastRequest: HttpRequest? = null
    var shouldThrow: Boolean = false

    override suspend fun execute(request: HttpRequest): HttpResponse {
        lastRequest = request
        if (shouldThrow) {
            throw RuntimeException("Test error")
        }
        return nextResponse
    }

    override fun close() {}
}
