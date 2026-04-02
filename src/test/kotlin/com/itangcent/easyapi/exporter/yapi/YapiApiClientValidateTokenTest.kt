package com.itangcent.easyapi.exporter.yapi

import com.itangcent.easyapi.exporter.yapi.model.TokenValidationResult
import com.itangcent.easyapi.http.HttpClient
import com.itangcent.easyapi.http.HttpRequest
import com.itangcent.easyapi.http.HttpResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class YapiApiClientValidateTokenTest {

    @Test
    fun `test validateToken returns Failed when serverUrl is blank`() = runBlocking {
        val client = YapiApiClient(serverUrl = "", token = "some-token", httpClient = StubHttpClient())
        val result = client.validateToken()

        assertTrue("Should be Failed when serverUrl is blank", result is TokenValidationResult.Failed)
        assertTrue(
            (result as TokenValidationResult.Failed).reason.contains("server URL", ignoreCase = true)
        )
    }

    @Test
    fun `test validateToken returns Failed when token is blank`() = runBlocking {
        val client = YapiApiClient(serverUrl = "http://localhost:3000", token = "", httpClient = StubHttpClient())
        val result = client.validateToken()

        assertTrue("Should be Failed when token is blank", result is TokenValidationResult.Failed)
        assertTrue(
            (result as TokenValidationResult.Failed).reason.contains("empty", ignoreCase = true)
        )
    }

    @Test
    fun `test validateToken returns Valid on successful response`() = runBlocking {
        val responseBody = """{"errcode":0,"errmsg":"ok","data":{"_id":"42"}}"""
        val httpClient = StubHttpClient(response = HttpResponse(code = 200, body = responseBody))
        val client = YapiApiClient(
            serverUrl = "http://localhost:3000",
            token = "valid-token",
            httpClient = httpClient
        )

        val result = client.validateToken()

        assertTrue("Should be Valid on success", result is TokenValidationResult.Valid)
        assertEquals("42", (result as TokenValidationResult.Valid).projectId)
    }

    @Test
    fun `test validateToken caches projectId after successful validation`() = runBlocking {
        val responseBody = """{"errcode":0,"errmsg":"ok","data":{"_id":"99"}}"""
        val callCount = intArrayOf(0)
        val httpClient = object : HttpClient {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                callCount[0]++
                return HttpResponse(code = 200, body = responseBody)
            }
            override fun close() {}
        }
        val client = YapiApiClient(
            serverUrl = "http://localhost:3000",
            token = "valid-token",
            httpClient = httpClient
        )

        val result1 = client.validateToken()
        assertEquals("99", (result1 as TokenValidationResult.Valid).projectId)

        val cachedId = client.getProjectId()
        assertEquals("99", cachedId)
        assertEquals("Second getProjectId should use cache", 1, callCount[0])
    }

    @Test
    fun `test validateToken returns Failed when errcode is non-zero`() = runBlocking {
        val responseBody = """{"errcode":40011,"errmsg":"invalid token"}"""
        val httpClient = StubHttpClient(response = HttpResponse(code = 200, body = responseBody))
        val client = YapiApiClient(
            serverUrl = "http://localhost:3000",
            token = "bad-token",
            httpClient = httpClient
        )

        val result = client.validateToken()

        assertTrue(result is TokenValidationResult.Failed)
        val failed = result as TokenValidationResult.Failed
        assertTrue(failed.reason.contains("invalid", ignoreCase = true))
        assertTrue(failed.reason.contains("40011"))
    }

    @Test
    fun `test validateToken returns Failed when data _id is missing`() = runBlocking {
        val responseBody = """{"errcode":0,"errmsg":"ok","data":{}}"""
        val httpClient = StubHttpClient(response = HttpResponse(code = 200, body = responseBody))
        val client = YapiApiClient(
            serverUrl = "http://localhost:3000",
            token = "token-without-id",
            httpClient = httpClient
        )

        val result = client.validateToken()

        assertTrue(result is TokenValidationResult.Failed)
        assertTrue(
            (result as TokenValidationResult.Failed).reason.contains("Could not resolve project ID")
        )
    }

    @Test
    fun `test validateToken returns Failed on non-200 HTTP status`() = runBlocking {
        val httpClient = StubHttpClient(response = HttpResponse(code = 500, body = "Internal Server Error"))
        val client = YapiApiClient(
            serverUrl = "http://localhost:3000",
            token = "some-token",
            httpClient = httpClient
        )

        val result = client.validateToken()

        assertTrue(result is TokenValidationResult.Failed)
        assertTrue(
            (result as TokenValidationResult.Failed).reason.contains("HTTP 500")
        )
    }

    @Test
    fun `test validateToken returns Failed on 404 HTTP status`() = runBlocking {
        val httpClient = StubHttpClient(response = HttpResponse(code = 404, body = "Not Found"))
        val client = YapiApiClient(
            serverUrl = "http://localhost:3000",
            token = "some-token",
            httpClient = httpClient
        )

        val result = client.validateToken()

        assertTrue(result is TokenValidationResult.Failed)
        assertTrue(
            (result as TokenValidationResult.Failed).reason.contains("HTTP 404")
        )
    }

    @Test
    fun `test validateToken returns Failed on network exception`() = runBlocking {
        val httpClient = StubHttpClient(exception = java.net.ConnectException("Connection refused"))
        val client = YapiApiClient(
            serverUrl = "http://localhost:3000",
            token = "some-token",
            httpClient = httpClient
        )

        val result = client.validateToken()

        assertTrue(result is TokenValidationResult.Failed)
        val reason = (result as TokenValidationResult.Failed).reason
        assertTrue(reason.contains("Cannot connect", ignoreCase = true))
        assertTrue(reason.contains("Connection refused"))
    }

    @Test
    fun `test validateToken returns Failed on timeout exception`() = runBlocking {
        val httpClient = StubHttpClient(exception = java.net.SocketTimeoutException("Read timed out"))
        val client = YapiApiClient(
            serverUrl = "http://localhost:3000",
            token = "some-token",
            httpClient = httpClient
        )

        val result = client.validateToken()

        assertTrue(result is TokenValidationResult.Failed)
        val reason = (result as TokenValidationResult.Failed).reason
        assertTrue(reason.contains("timeout", ignoreCase = true))
        assertTrue(reason.contains("Settings > EasyApi > Http"))
    }

    @Test
    fun `test validateToken returns Failed on unknown host exception`() = runBlocking {
        val httpClient = StubHttpClient(exception = java.net.UnknownHostException("invalid-host"))
        val client = YapiApiClient(
            serverUrl = "http://invalid-host:3000",
            token = "some-token",
            httpClient = httpClient
        )

        val result = client.validateToken()

        assertTrue(result is TokenValidationResult.Failed)
        val reason = (result as TokenValidationResult.Failed).reason
        assertTrue(reason.contains("Cannot resolve", ignoreCase = true))
        assertTrue(reason.contains("invalid-host"))
    }

    @Test
    fun `test validateToken includes URL in failure reasons`() = runBlocking {
        val httpClient = StubHttpClient(exception = RuntimeException("something went wrong"))
        val client = YapiApiClient(
            serverUrl = "http://my-yapi-server:4000",
            token = "abc123",
            httpClient = httpClient
        )

        val result = client.validateToken()

        assertTrue(result is TokenValidationResult.Failed)
        val reason = (result as TokenValidationResult.Failed).reason
        assertTrue("Failure reason should contain URL", reason.contains("URL:"))
        assertTrue("Failure reason should contain server address", reason.contains("my-yapi-server"))
    }

    @Test
    fun `test validateToken with whitespace-only serverUrl treats it as blank`() = runBlocking {
        val client = YapiApiClient(serverUrl = "   ", token = "token", httpClient = StubHttpClient())
        val result = client.validateToken()

        assertTrue(result is TokenValidationResult.Failed)
        assertTrue(
            (result as TokenValidationResult.Failed).reason.contains("server URL", ignoreCase = true)
        )
    }

    private class StubHttpClient(
        private val response: HttpResponse? = null,
        private val exception: Exception? = null
    ) : HttpClient {
        override suspend fun execute(request: HttpRequest): HttpResponse {
            exception?.let { throw it }
            return response ?: HttpResponse(code = 200, body = "")
        }
        override fun close() {}
    }
}
