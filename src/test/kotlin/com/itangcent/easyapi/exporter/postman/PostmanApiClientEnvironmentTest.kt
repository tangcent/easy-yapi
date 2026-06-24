package com.itangcent.easyapi.exporter.postman

import com.itangcent.easyapi.exporter.postman.model.PostmanEnvironmentDetail
import com.itangcent.easyapi.exporter.postman.model.PostmanEnvironmentValue
import com.itangcent.easyapi.http.HttpRequest
import com.itangcent.easyapi.http.HttpResponse
import com.itangcent.easyapi.http.name
import com.itangcent.easyapi.http.value
import com.itangcent.easyapi.script.env.Environment
import com.itangcent.easyapi.script.env.EnvironmentScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for [PostmanApiClient] environment API methods and companion mapping helpers.
 *
 * Spec: requirements/REQ-4 (API Client Extension), tasks/2.5, tasks/2.6
 */
class PostmanApiClientEnvironmentTest {

    private lateinit var mockClient: MockHttpClient
    private lateinit var apiClient: PostmanApiClient

    @Before
    fun setUp() {
        mockClient = MockHttpClient()
        apiClient = PostmanApiClient(
            apiKey = "test-api-key",
            workspaceId = "ws-123",
            httpClient = mockClient
        )
    }

    // ── Companion: environmentToVariables (spec task 2.5) ────────────────────

    @Test
    fun `environmentToVariables maps each variable to PostmanEnvironmentValue`() {
        val env = Environment(
            name = "dev",
            variables = mapOf("URL" to "http://localhost", "KEY" to "secret")
        )

        val values = PostmanApiClient.environmentToVariables(env)

        assertEquals(2, values.size)
        assertTrue(values.any { it.key == "URL" && it.value == "http://localhost" })
        assertTrue(values.any { it.key == "KEY" && it.value == "secret" })
        values.forEach {
            assertTrue(it.enabled)
            assertEquals("text", it.type)
        }
    }

    @Test
    fun `environmentToVariables returns empty list for empty environment`() {
        val env = Environment(name = "empty", variables = emptyMap())
        val values = PostmanApiClient.environmentToVariables(env)
        assertTrue(values.isEmpty())
    }

    // ── Companion: postmanVariablesToMap (spec task 2.6) ────────────────────

    @Test
    fun `postmanVariablesToMap excludes disabled by default`() {
        val values = listOf(
            PostmanEnvironmentValue("A", "1", enabled = true),
            PostmanEnvironmentValue("B", "2", enabled = false)
        )

        val map = PostmanApiClient.postmanVariablesToMap(values, includeDisabled = false)

        assertEquals(1, map.size)
        assertEquals("1", map["A"])
        assertNull(map["B"])
    }

    @Test
    fun `postmanVariablesToMap includes disabled when flag set`() {
        val values = listOf(
            PostmanEnvironmentValue("A", "1", enabled = true),
            PostmanEnvironmentValue("B", "2", enabled = false)
        )

        val map = PostmanApiClient.postmanVariablesToMap(values, includeDisabled = true)

        assertEquals(2, map.size)
        assertEquals("1", map["A"])
        assertEquals("2", map["B"])
    }

    // ── listEnvironments ─────────────────────────────────────────────────────

    @Test
    fun `listEnvironments returns empty list for blank api key`() = runBlocking {
        val client = PostmanApiClient(apiKey = "", httpClient = mockClient)
        val result = client.listEnvironments("ws-1")
        assertTrue(result.isEmpty())
        // No HTTP call should be made
        assertNull(mockClient.lastRequest)
    }

    @Test
    fun `listEnvironments parses environments array on 200`() = runBlocking {
        mockClient.nextResponse = HttpResponse(
            code = 200,
            body = """{"environments":[{"id":"e1","name":"dev","uid":"u1"},{"id":"e2","name":"prod"}]}"""
        )

        val result = apiClient.listEnvironments("ws-123")

        assertEquals(2, result.size)
        assertEquals("e1", result[0].id)
        assertEquals("dev", result[0].name)
        assertEquals("u1", result[0].uid)
        assertEquals("e2", result[1].id)
        assertEquals("prod", result[1].name)
        assertNull(result[1].uid)
        // Verify request
        assertEquals("GET", mockClient.lastRequest?.method)
        assertTrue(mockClient.lastRequest?.url?.contains("/environments?workspace=ws-123") == true)
        assertTrue(mockClient.lastRequest?.headers?.any { it.name == "X-Api-Key" && it.value == "test-api-key" } == true)
    }

    @Test
    fun `listEnvironments returns empty list on non-200`() = runBlocking {
        mockClient.nextResponse = HttpResponse(code = 401, body = "Unauthorized")
        val result = apiClient.listEnvironments("ws-123")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `listEnvironments returns empty list on parse error`() = runBlocking {
        mockClient.nextResponse = HttpResponse(code = 200, body = "not json")
        val result = apiClient.listEnvironments("ws-123")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `listEnvironments handles empty environments array`() = runBlocking {
        mockClient.nextResponse = HttpResponse(code = 200, body = """{"environments":[]}""")
        val result = apiClient.listEnvironments("ws-123")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `listEnvironments handles missing environments key`() = runBlocking {
        mockClient.nextResponse = HttpResponse(code = 200, body = """{"other":"data"}""")
        val result = apiClient.listEnvironments("ws-123")
        assertTrue(result.isEmpty())
    }

    // ── getEnvironment ───────────────────────────────────────────────────────

    @Test
    fun `getEnvironment returns null for blank api key`() = runBlocking {
        val client = PostmanApiClient(apiKey = "", httpClient = mockClient)
        val result = client.getEnvironment("env-1")
        assertNull(result)
        assertNull(mockClient.lastRequest)
    }

    @Test
    fun `getEnvironment parses environment detail on 200`() = runBlocking {
        mockClient.nextResponse = HttpResponse(
            code = 200,
            body = """{"environment":{"id":"e1","name":"dev","uid":"u1","values":[
                {"key":"URL","value":"http://x","enabled":true,"type":"text"},
                {"key":"TOKEN","value":"secret","enabled":false,"type":"secret"}
            ]}}"""
        )

        val result = apiClient.getEnvironment("e1")

        assertNotNull(result)
        assertEquals("e1", result!!.id)
        assertEquals("dev", result.name)
        assertEquals("u1", result.uid)
        assertEquals(2, result.values.size)
        assertEquals("URL", result.values[0].key)
        assertEquals("http://x", result.values[0].value)
        assertTrue(result.values[0].enabled)
        assertEquals("TOKEN", result.values[1].key)
        assertFalse(result.values[1].enabled)
        assertEquals("secret", result.values[1].type)

        // Verify request
        assertEquals("GET", mockClient.lastRequest?.method)
        assertTrue(mockClient.lastRequest?.url?.endsWith("/environments/e1") == true)
    }

    @Test
    fun `getEnvironment returns null on non-200`() = runBlocking {
        mockClient.nextResponse = HttpResponse(code = 404, body = "Not found")
        val result = apiClient.getEnvironment("e1")
        assertNull(result)
    }

    @Test
    fun `getEnvironment returns null on parse error`() = runBlocking {
        mockClient.nextResponse = HttpResponse(code = 200, body = "invalid")
        val result = apiClient.getEnvironment("e1")
        assertNull(result)
    }

    @Test
    fun `getEnvironment handles missing values array`() = runBlocking {
        mockClient.nextResponse = HttpResponse(
            code = 200,
            body = """{"environment":{"id":"e1","name":"dev"}}"""
        )
        val result = apiClient.getEnvironment("e1")
        assertNotNull(result)
        assertEquals("dev", result!!.name)
        assertTrue(result.values.isEmpty())
    }

    @Test
    fun `getEnvironment handles missing optional fields`() = runBlocking {
        mockClient.nextResponse = HttpResponse(
            code = 200,
            body = """{"environment":{"name":"dev","values":[{"key":"A","value":"1"}]}}"""
        )
        val result = apiClient.getEnvironment("e1")
        assertNotNull(result)
        assertEquals("", result!!.id)
        assertNull(result.uid)
        assertEquals(1, result.values.size)
        // Defaults: enabled=true, type=text
        assertTrue(result.values[0].enabled)
        assertEquals("text", result.values[0].type)
    }

    // ── createEnvironment ────────────────────────────────────────────────────

    @Test
    fun `createEnvironment returns mock success for blank api key`() = runBlocking {
        val client = PostmanApiClient(apiKey = "", httpClient = mockClient)
        val env = PostmanEnvironmentDetail(name = "dev", values = listOf(PostmanEnvironmentValue("A", "1")))

        val result = client.createEnvironment("ws-1", env)

        assertTrue(result.success)
        assertTrue(result.message?.contains("Mock mode") == true)
        assertNull(mockClient.lastRequest)
    }

    @Test
    fun `createEnvironment returns success on 200`() = runBlocking {
        mockClient.nextResponse = HttpResponse(code = 200, body = """{"environment":{"id":"e1"}}""")
        val env = PostmanEnvironmentDetail(name = "dev", values = listOf(PostmanEnvironmentValue("A", "1")))

        val result = apiClient.createEnvironment("ws-123", env)

        assertTrue(result.success)
        assertEquals("POST", mockClient.lastRequest?.method)
        assertTrue(mockClient.lastRequest?.url?.contains("/environments?workspace=ws-123") == true)
        assertTrue(mockClient.lastRequest?.headers?.any { it.name == "Content-Type" && it.value == "application/json" } == true)
        // Body should contain the environment wrapper
        assertTrue(mockClient.lastRequest?.body?.contains("\"environment\"") == true)
        assertTrue(mockClient.lastRequest?.body?.contains("\"name\":\"dev\"") == true)
    }

    @Test
    fun `createEnvironment returns failure on non-200`() = runBlocking {
        mockClient.nextResponse = HttpResponse(code = 400, body = "Bad request")
        val env = PostmanEnvironmentDetail(name = "dev")

        val result = apiClient.createEnvironment("ws-123", env)

        assertFalse(result.success)
        assertTrue(result.message?.contains("HTTP 400") == true)
    }

    @Test
    fun `createEnvironment returns failure on exception`() = runBlocking {
        mockClient.shouldThrow = true
        val env = PostmanEnvironmentDetail(name = "dev")

        val result = apiClient.createEnvironment("ws-123", env)

        assertFalse(result.success)
        assertEquals("Test error", result.message)
    }

    // ── updateEnvironment ────────────────────────────────────────────────────

    @Test
    fun `updateEnvironment returns mock success for blank api key`() = runBlocking {
        val client = PostmanApiClient(apiKey = "", httpClient = mockClient)
        val env = PostmanEnvironmentDetail(name = "dev")

        val result = client.updateEnvironment("e1", env)

        assertTrue(result.success)
        assertTrue(result.message?.contains("Mock mode") == true)
        assertNull(mockClient.lastRequest)
    }

    @Test
    fun `updateEnvironment returns success on 200`() = runBlocking {
        mockClient.nextResponse = HttpResponse(code = 200, body = """{"environment":{"id":"e1"}}""")
        val env = PostmanEnvironmentDetail(name = "dev", values = listOf(PostmanEnvironmentValue("A", "1")))

        val result = apiClient.updateEnvironment("e1", env)

        assertTrue(result.success)
        assertEquals("PUT", mockClient.lastRequest?.method)
        assertTrue(mockClient.lastRequest?.url?.endsWith("/environments/e1") == true)
        assertTrue(mockClient.lastRequest?.body?.contains("\"environment\"") == true)
    }

    @Test
    fun `updateEnvironment returns failure on non-200`() = runBlocking {
        mockClient.nextResponse = HttpResponse(code = 404, body = "Not found")
        val env = PostmanEnvironmentDetail(name = "dev")

        val result = apiClient.updateEnvironment("e1", env)

        assertFalse(result.success)
        assertTrue(result.message?.contains("HTTP 404") == true)
    }

    @Test
    fun `updateEnvironment returns failure on exception`() = runBlocking {
        mockClient.shouldThrow = true
        val env = PostmanEnvironmentDetail(name = "dev")

        val result = apiClient.updateEnvironment("e1", env)

        assertFalse(result.success)
        assertEquals("Test error", result.message)
    }

    /**
     * Simple mock HttpClient that returns a pre-configured response.
     */
    class MockHttpClient : com.itangcent.easyapi.http.HttpClient {
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
}
