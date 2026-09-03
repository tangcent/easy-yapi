package com.itangcent.easyapi.core.rule.context

import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.HttpMetadata
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.ParameterBinding
import com.itangcent.easyapi.core.export.httpMetadata
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
    @Suppress("DEPRECATION")
    fun testSetParam() {
        // Legacy 4-arg form (pre-example rules, commit 7127d9d2) — deprecated
        // but must keep resolving for historical .rules; example stays null.
        scriptEndpoint.setParam("userId", "123", true, "User ID")
        val param = endpoint.httpMetadata?.parameters?.last()
        assertNotNull("Should add a parameter", param)
        assertNull("Legacy call leaves example null", param?.example)
    }

    @Test
    fun testSetParamWithExample() {
        scriptEndpoint.setParam("userId", "123", true, "User ID", "user-001")
        val param = endpoint.httpMetadata?.parameters?.last()
        assertNotNull("Should add a parameter", param)
        assertEquals(ParameterBinding.Query, param?.binding)
        assertEquals("user-001", param?.example)
    }

    @Test
    @Suppress("DEPRECATION")
    fun testSetFormParam() {
        // Legacy 4-arg form — kept for historical rules, example stays null.
        scriptEndpoint.setFormParam("username", "john", true, "Username")
        assertEquals(ParameterBinding.Form, endpoint.httpMetadata?.parameters?.last()?.binding)
    }

    @Test
    fun testSetFormParamWithExample() {
        scriptEndpoint.setFormParam("username", "john", true, "Username", "jdoe")
        val param = endpoint.httpMetadata?.parameters?.last()
        assertEquals(ParameterBinding.Form, param?.binding)
        assertEquals("jdoe", param?.example)
    }

    @Test
    @Suppress("DEPRECATION")
    fun testSetPathParam() {
        // Legacy 3-arg form — kept for historical rules, example stays null.
        scriptEndpoint.setPathParam("id", "123", "Path ID")
        assertEquals(ParameterBinding.Path, endpoint.httpMetadata?.parameters?.last()?.binding)
    }

    @Test
    fun testSetPathParamWithExample() {
        scriptEndpoint.setPathParam("id", "123", "Path ID", "42")
        val param = endpoint.httpMetadata?.parameters?.last()
        assertEquals(ParameterBinding.Path, param?.binding)
        assertEquals("42", param?.example)
    }

    @Test
    @Suppress("DEPRECATION")
    fun testSetHeader() {
        // Legacy 4-arg form — kept for historical rules, example stays null.
        scriptEndpoint.setHeader("X-Custom", "value", true, "Custom header")
        assertEquals("X-Custom", endpoint.httpMetadata?.headers?.last()?.name)
    }

    @Test
    fun testSetHeaderWithExample() {
        scriptEndpoint.setHeader("X-Custom", "value", true, "Custom header", "abc")
        val header = endpoint.httpMetadata?.headers?.last()
        assertEquals("abc", header?.example)
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
    @Suppress("DEPRECATION")
    fun testSetResponseHeader() {
        // Legacy 4-arg form — kept for historical rules, example stays null.
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
