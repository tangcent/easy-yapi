package com.itangcent.easyapi.core.rule.context

import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.HttpMetadata
import com.itangcent.easyapi.core.export.HttpMethod
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ScriptApiEndpointTest {

    private lateinit var endpoint: ApiEndpoint
    private lateinit var scriptEndpoint: ScriptApiEndpoint

    @Before
    fun setUp() {
        val metadata = HttpMetadata(
            method = HttpMethod.GET,
            path = "/api/users"
        )
        endpoint = ApiEndpoint(name = "testEndpoint", metadata = metadata)
        scriptEndpoint = ScriptApiEndpoint(endpoint)
    }

    @Test
    fun testName() {
        assertEquals("testEndpoint", scriptEndpoint.name())
    }

    @Test
    fun testPath() {
        assertEquals("/api/users", scriptEndpoint.path())
    }

    @Test
    fun testMethod() {
        assertEquals("GET", scriptEndpoint.method())
    }

    @Test
    fun testSetPath() {
        scriptEndpoint.setPath("/api/v2/users")
        assertEquals("/api/v2/users", scriptEndpoint.path())
    }

    @Test
    fun testSetMethod() {
        scriptEndpoint.setMethod("POST")
        assertEquals("POST", scriptEndpoint.method())
    }

    @Test
    fun testSetMethodCaseInsensitive() {
        scriptEndpoint.setMethod("post")
        assertEquals("POST", scriptEndpoint.method())
    }

    @Test
    fun testDescription() {
        assertNull("Description should be null initially", scriptEndpoint.description())
        scriptEndpoint.setDescription("A test endpoint")
        assertEquals("A test endpoint", scriptEndpoint.description())
    }

    @Test
    fun testAppendDesc() {
        scriptEndpoint.setDescription("Base")
        scriptEndpoint.appendDesc(" extra")
        assertTrue("Should append description", scriptEndpoint.description()!!.contains("extra"))
    }

    @Test
    fun testToString() {
        assertNotNull("toString should not be null", scriptEndpoint.toString())
    }

    @Test
    fun testSetMethodInvalid() {
        scriptEndpoint.setMethod("INVALID")
        // Should remain GET since INVALID is not a valid HttpMethod
        assertEquals("GET", scriptEndpoint.method())
    }

    @Test
    fun testSetParam() {
        scriptEndpoint.setParam("userId", "123", true, "User ID")
        // Verify no exception thrown
    }

    @Test
    fun testSetFormParam() {
        scriptEndpoint.setFormParam("username", "john", true, "Username")
    }

    @Test
    fun testSetPathParam() {
        scriptEndpoint.setPathParam("id", "123", "Path ID")
    }

    @Test
    fun testSetHeader() {
        scriptEndpoint.setHeader("X-Custom", "value", true, "Custom header")
    }

    @Test
    fun testSetResponseCode() {
        scriptEndpoint.setResponseCode(200)
    }

    @Test
    fun testAppendResponseBodyDesc() {
        scriptEndpoint.appendResponseBodyDesc("User object")
    }

    @Test
    fun testSetResponseHeader() {
        scriptEndpoint.setResponseHeader("X-Request-Id", "abc", false, "Request ID")
    }

    @Test
    fun testSetResponseBodyClass() {
        scriptEndpoint.setResponseBodyClass("com.example.User")
    }

    @Test
    fun testPathWithNonHttpMetadata() {
        val grpcEndpoint = ApiEndpoint(
            name = "grpcEndpoint",
            metadata = com.itangcent.easyapi.core.export.GrpcMetadata(
                path = "/service/method",
                serviceName = "Service",
                methodName = "Method",
                packageName = "com.example",
                streamingType = com.itangcent.easyapi.core.export.GrpcStreamingType.UNARY
            )
        )
        val scriptGrpc = ScriptApiEndpoint(grpcEndpoint)
        assertNull("Path should be null for non-HTTP metadata", scriptGrpc.path())
        assertNull("Method should be null for non-HTTP metadata", scriptGrpc.method())
    }

    @Test
    fun testSetPathWithNonHttpMetadata() {
        val grpcEndpoint = ApiEndpoint(
            name = "grpcEndpoint",
            metadata = com.itangcent.easyapi.core.export.GrpcMetadata(
                path = "/service/method",
                serviceName = "Service",
                methodName = "Method",
                packageName = "com.example",
                streamingType = com.itangcent.easyapi.core.export.GrpcStreamingType.UNARY
            )
        )
        val scriptGrpc = ScriptApiEndpoint(grpcEndpoint)
        scriptGrpc.setPath("/new/path") // Should not throw
        assertNull("Path should still be null", scriptGrpc.path())
    }

    @Test
    fun testSetMethodWithNonHttpMetadata() {
        val grpcEndpoint = ApiEndpoint(
            name = "grpcEndpoint",
            metadata = com.itangcent.easyapi.core.export.GrpcMetadata(
                path = "/service/method",
                serviceName = "Service",
                methodName = "Method",
                packageName = "com.example",
                streamingType = com.itangcent.easyapi.core.export.GrpcStreamingType.UNARY
            )
        )
        val scriptGrpc = ScriptApiEndpoint(grpcEndpoint)
        scriptGrpc.setMethod("POST") // Should not throw
        assertNull("Method should still be null", scriptGrpc.method())
    }
}
