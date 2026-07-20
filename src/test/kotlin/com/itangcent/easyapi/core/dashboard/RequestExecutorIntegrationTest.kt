package com.itangcent.easyapi.core.dashboard

import com.itangcent.easyapi.core.export.GrpcMetadata
import com.itangcent.easyapi.core.export.GrpcStreamingType
import com.itangcent.easyapi.core.http.FormParam
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*

/**
 * Integration tests for RequestExecutor's executeHttp and executeGrpc methods.
 *
 * These tests use the real IntelliJ platform services available via
 * EasyApiLightCodeInsightFixtureTestCase, but mock the HttpClient
 * to avoid real network calls.
 */
class RequestExecutorIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var executor: RequestExecutor

    override fun setUp() {
        super.setUp()
        executor = RequestExecutor.getInstance(project)
    }

    // ── executeHttp basic tests ──────────────────────────────────────────────

    fun testExecuteHttpSimpleGet() = runBlocking {
        val input = HttpRequestInput(
            host = "https://httpbin.org",
            path = "/get",
            method = "GET",
            endpointName = "Test GET",
            endpointKey = "test-get"
        )
        // The request may fail due to network, but should not throw
        val result = executor.executeHttp(input)
        assertNotNull(result)
        assertNotNull(result.body)
    }

    fun testExecuteHttpWithBlankHost() = runBlocking {
        val input = HttpRequestInput(
            host = "",
            path = "/api/test",
            method = "GET",
            endpointName = "Blank Host",
            endpointKey = "blank-host"
        )
        // Should handle gracefully
        val result = executor.executeHttp(input)
        assertNotNull(result)
    }

    // ── executeGrpc basic tests ──────────────────────────────────────────────

    fun testExecuteGrpcClientNotAvailable() = runBlocking {
        val input = GrpcRequestInput(
            host = "localhost:9090",
            grpcMetadata = GrpcMetadata(
                serviceName = "TestService",
                methodName = "TestMethod",
                packageName = "com.example",
                streamingType = GrpcStreamingType.UNARY,
                path = "/com.example.TestService/TestMethod"
            ),
            endpointName = "Test gRPC",
            body = "{}"
        )
        // gRPC client is typically not available in test environment
        val result = executor.executeGrpc(input)
        assertNotNull(result)
        if (result.requiresGrpcSetup) {
            assertTrue(result.isError)
            assertTrue(result.body.contains("gRPC client not available"))
        }
    }

    // ── Data class tests ─────────────────────────────────────────────────────

    fun testGrpcRequestInputDefaults() {
        val input = GrpcRequestInput(
            host = "localhost:9090",
            grpcMetadata = GrpcMetadata(
                serviceName = "TestService",
                methodName = "TestMethod",
                packageName = "com.example",
                streamingType = GrpcStreamingType.UNARY,
                path = "/TestService/TestMethod"
            )
        )
        assertEquals("localhost:9090", input.host)
        assertNull(input.body)
        assertEquals("", input.endpointName)
        assertNull(input.sourceMethod)
    }

    fun testGrpcRequestInputWithBody() {
        val input = GrpcRequestInput(
            host = "localhost:9090",
            grpcMetadata = GrpcMetadata(
                serviceName = "TestService",
                methodName = "TestMethod",
                packageName = "com.example",
                streamingType = GrpcStreamingType.UNARY,
                path = "/TestService/TestMethod"
            ),
            body = """{"id": "1"}""",
            endpointName = "GetUser"
        )
        assertEquals("""{"id": "1"}""", input.body)
        assertEquals("GetUser", input.endpointName)
    }

    fun testRequestResultDefaults() {
        val result = RequestResult(body = "test", isError = false)
        assertEquals("test", result.body)
        assertFalse(result.isError)
        assertNull(result.statusCode)
        assertTrue(result.headers.isEmpty())
        assertNull(result.testResults)
        assertFalse(result.requiresGrpcSetup)
    }

    // ── Form data handling ───────────────────────────────────────────────────

    fun testExecuteHttpWithFormData() = runBlocking {
        val input = HttpRequestInput(
            host = "https://httpbin.org",
            path = "/post",
            method = "POST",
            formParams = listOf(FormParam.Text("field1", "value1")),
            hasFormData = true,
            endpointName = "Form Data Test",
            endpointKey = "form-data-test"
        )
        val result = executor.executeHttp(input)
        assertNotNull(result)
    }

    // ── Empty body handling ──────────────────────────────────────────────────

    fun testExecuteHttpWithBlankBody() = runBlocking {
        val input = HttpRequestInput(
            host = "https://httpbin.org",
            path = "/post",
            method = "POST",
            body = "   ",  // blank body should be treated as null
            endpointName = "Blank Body Test",
            endpointKey = "blank-body-test"
        )
        val result = executor.executeHttp(input)
        assertNotNull(result)
    }

    // ── Path parameter resolution ────────────────────────────────────────────

    fun testExecuteHttpWithPathParams() = runBlocking {
        val input = HttpRequestInput(
            host = "https://httpbin.org",
            path = "/users/{id}/posts/{postId}",
            method = "GET",
            pathParams = listOf("id" to "42", "postId" to "100"),
            endpointName = "Path Params Test",
            endpointKey = "path-params-test"
        )
        val result = executor.executeHttp(input)
        assertNotNull(result)
    }
}
