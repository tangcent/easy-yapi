package com.itangcent.easyapi.exporter.model

import org.junit.Assert.*
import org.junit.Test

class ApiEndpointTest {

    private fun httpEndpoint(
        path: String = "/api/test",
        method: HttpMethod = HttpMethod.GET,
        description: String? = null
    ) = ApiEndpoint(
        name = "test",
        description = description,
        metadata = httpMetadata(path = path, method = method)
    )

    private fun grpcEndpoint() = ApiEndpoint(
        name = "grpc-test",
        metadata = GrpcMetadata(
            path = "/test.Service/Method",
            serviceName = "Service",
            methodName = "Method",
            packageName = "test",
            streamingType = GrpcStreamingType.UNARY
        )
    )

    // --- Extension properties ---

    @Test
    fun testIsHttp() {
        assertTrue(httpEndpoint().isHttp)
        assertFalse(grpcEndpoint().isHttp)
    }

    @Test
    fun testIsGrpc() {
        assertFalse(httpEndpoint().isGrpc)
        assertTrue(grpcEndpoint().isGrpc)
    }

    @Test
    fun testHttpMetadata() {
        val ep = httpEndpoint()
        assertNotNull(ep.httpMetadata)
        assertEquals("/api/test", ep.httpMetadata!!.path)
    }

    @Test
    fun testHttpMetadataReturnsNullForGrpc() {
        assertNull(grpcEndpoint().httpMetadata)
    }

    @Test
    fun testGrpcMetadata() {
        val ep = grpcEndpoint()
        assertNotNull(ep.grpcMetadata)
        assertEquals("Service", ep.grpcMetadata!!.serviceName)
    }

    @Test
    fun testGrpcMetadataReturnsNullForHttp() {
        assertNull(httpEndpoint().grpcMetadata)
    }

    @Test
    fun testPathExtensionForHttp() {
        assertEquals("/api/test", httpEndpoint().path)
    }

    @Test
    fun testPathExtensionForGrpc() {
        assertEquals("/test.Service/Method", grpcEndpoint().path)
    }

    // --- Mutation methods on HttpMetadata ---

    @Test
    fun testSetParam() {
        val ep = httpEndpoint()
        ep.setParam("search", "default", true, "Search term")
        val params = ep.httpMetadata!!.parameters
        assertEquals(1, params.size)
        assertEquals("search", params[0].name)
        assertEquals("default", params[0].defaultValue)
        assertTrue(params[0].required)
        assertEquals("Search term", params[0].description)
        assertEquals(ParameterBinding.Query, params[0].binding)
    }

    @Test
    fun testSetFormParam() {
        val ep = httpEndpoint()
        ep.setFormParam("username", null, true, "Username")
        val params = ep.httpMetadata!!.parameters
        assertEquals(1, params.size)
        assertEquals(ParameterBinding.Form, params[0].binding)
    }

    @Test
    fun testSetPathParam() {
        val ep = httpEndpoint()
        ep.setPathParam("id", "0", "User ID")
        val params = ep.httpMetadata!!.parameters
        assertEquals(1, params.size)
        assertEquals(ParameterBinding.Path, params[0].binding)
        assertTrue(params[0].required)
    }

    @Test
    fun testSetHeader() {
        val ep = httpEndpoint()
        ep.setHeader("Authorization", "Bearer token", true, "Auth header")
        val headers = ep.httpMetadata!!.headers
        assertEquals(1, headers.size)
        assertEquals("Authorization", headers[0].name)
        assertEquals("Bearer token", headers[0].value)
    }

    @Test
    fun testSetResponseCode() {
        val ep = httpEndpoint()
        // setResponseCode is a no-op for script compatibility
        ep.setResponseCode(200)
    }

    @Test
    fun testAppendResponseBodyDesc() {
        val ep = httpEndpoint()
        // appendResponseBodyDesc is a no-op for script compatibility
        ep.appendResponseBodyDesc("OK")
    }

    @Test
    fun testSetResponseHeader() {
        val ep = httpEndpoint()
        ep.setResponseHeader("X-Custom", "value", false, "Custom header")
        val headers = ep.httpMetadata!!.headers
        assertEquals(1, headers.size)
        assertEquals("X-Custom", headers[0].name)
    }

    @Test
    fun testSetResponseBodyClass() {
        val ep = httpEndpoint()
        ep.setResponseBodyClass("com.example.User")
        assertEquals("com.example.User", ep.httpMetadata!!.responseType)
    }

    @Test
    fun testAppendDesc() {
        val ep = httpEndpoint(description = "Base")
        ep.appendDesc(" - extended")
        assertEquals("Base - extended", ep.description)
    }

    @Test
    fun testAppendDescNull() {
        val ep = httpEndpoint(description = null)
        ep.appendDesc("added")
        assertEquals("added", ep.description)
    }

    @Test
    fun testAppendDescNullArgument() {
        val ep = httpEndpoint(description = "Base")
        ep.appendDesc(null)
        assertEquals("Base", ep.description)
    }

    // --- Mutation methods on GrpcMetadata (should be no-ops) ---

    @Test
    fun testSetParamOnGrpcEndpoint() {
        val ep = grpcEndpoint()
        ep.setParam("search", "default", true, "Search term")
        // GrpcMetadata has no parameters list, so this is a no-op
    }

    @Test
    fun testSetFormParamOnGrpcEndpoint() {
        val ep = grpcEndpoint()
        ep.setFormParam("username", null, true, "Username")
        // No-op
    }

    @Test
    fun testSetHeaderOnGrpcEndpoint() {
        val ep = grpcEndpoint()
        ep.setHeader("Authorization", "Bearer token", true, "Auth header")
        // No-op
    }

    @Test
    fun testSetPathParamOnGrpcEndpoint() {
        val ep = grpcEndpoint()
        ep.setPathParam("id", "0", "User ID")
        // No-op
    }

    @Test
    fun testSetResponseHeaderOnGrpcEndpoint() {
        val ep = grpcEndpoint()
        ep.setResponseHeader("X-Custom", "value", false, "Custom header")
        // No-op
    }

    @Test
    fun testSetResponseBodyClassOnGrpcEndpoint() {
        val ep = grpcEndpoint()
        ep.setResponseBodyClass("com.example.User")
        // No-op
    }
}

class HttpMetadataTest {

    @Test
    fun testProtocol() {
        val meta = httpMetadata(path = "/api/test", method = HttpMethod.GET)
        assertEquals("HTTP", meta.protocol)
    }

    @Test
    fun testDefaultValues() {
        val meta = httpMetadata(path = "/api/test", method = HttpMethod.POST)
        assertTrue(meta.parameters.isEmpty())
        assertTrue(meta.headers.isEmpty())
        assertNull(meta.contentType)
        assertNull(meta.bodyAttr)
        assertNull(meta.alternativePaths)
        assertNull(meta.body)
        assertNull(meta.responseBody)
        assertNull(meta.responseType)
    }

    @Test
    fun testMutableParameters() {
        val meta = httpMetadata(path = "/api/test", method = HttpMethod.GET)
        meta.parameters.add(ApiParameter(name = "id", binding = ParameterBinding.Query))
        assertEquals(1, meta.parameters.size)
    }

    @Test
    fun testMutableHeaders() {
        val meta = httpMetadata(path = "/api/test", method = HttpMethod.GET)
        meta.headers.add(ApiHeader(name = "Content-Type", value = "application/json"))
        assertEquals(1, meta.headers.size)
    }

    @Test
    fun testAlternativePaths() {
        val meta = httpMetadata(
            path = "/api/test",
            method = HttpMethod.GET,
            alternativePaths = listOf("/api/v2/test")
        )
        assertEquals(1, meta.alternativePaths!!.size)
        meta.alternativePaths!!.add("/api/v3/test")
        assertEquals(2, meta.alternativePaths!!.size)
    }
}

class ParameterTypePureTest {

    @Test
    fun testFromTypeName_null() {
        assertEquals(ParameterType.TEXT, ParameterType.fromTypeName(null))
    }

    @Test
    fun testFromTypeName_blank() {
        assertEquals(ParameterType.TEXT, ParameterType.fromTypeName(""))
        assertEquals(ParameterType.TEXT, ParameterType.fromTypeName("   "))
    }

    @Test
    fun testFromTypeName_file() {
        assertEquals(ParameterType.FILE, ParameterType.fromTypeName("file"))
        assertEquals(ParameterType.FILE, ParameterType.fromTypeName("MultipartFile"))
    }

    @Test
    fun testFromTypeName_text() {
        assertEquals(ParameterType.TEXT, ParameterType.fromTypeName("String"))
        assertEquals(ParameterType.TEXT, ParameterType.fromTypeName("int"))
    }

    @Test
    fun testRawType() {
        assertEquals("text", ParameterType.TEXT.rawType())
        assertEquals("file", ParameterType.FILE.rawType())
    }
}

class HttpMethodPureTest {

    @Test
    fun testFromSpring() {
        assertEquals(HttpMethod.GET, HttpMethod.fromSpring("GET"))
        assertEquals(HttpMethod.POST, HttpMethod.fromSpring("POST"))
        assertEquals(HttpMethod.PUT, HttpMethod.fromSpring("PUT"))
        assertEquals(HttpMethod.DELETE, HttpMethod.fromSpring("DELETE"))
        assertEquals(HttpMethod.PATCH, HttpMethod.fromSpring("PATCH"))
        assertEquals(HttpMethod.HEAD, HttpMethod.fromSpring("HEAD"))
        assertEquals(HttpMethod.OPTIONS, HttpMethod.fromSpring("OPTIONS"))
    }

    @Test
    fun testFromSpring_caseInsensitive() {
        assertEquals(HttpMethod.GET, HttpMethod.fromSpring("get"))
        assertEquals(HttpMethod.POST, HttpMethod.fromSpring("post"))
    }

    @Test
    fun testFromSpring_unknown() {
        assertNull(HttpMethod.fromSpring("UNKNOWN"))
        assertNull(HttpMethod.fromSpring("TRACE"))
    }

    @Test
    fun testAllMethods() {
        assertEquals(8, HttpMethod.values().size)
    }
}

class ParameterBindingPureTest {

    @Test
    fun testBindingTypes() {
        assertNotNull(ParameterBinding.Query)
        assertNotNull(ParameterBinding.Path)
        assertNotNull(ParameterBinding.Header)
        assertNotNull(ParameterBinding.Cookie)
        assertNotNull(ParameterBinding.Body)
        assertNotNull(ParameterBinding.Form)
        assertNotNull(ParameterBinding.Ignored)
    }

    @Test
    fun testBindingSingletons() {
        assertSame(ParameterBinding.Query, ParameterBinding.Query)
        assertSame(ParameterBinding.Path, ParameterBinding.Path)
    }
}

class ApiParameterPureTest {

    @Test
    fun testDefaultValues() {
        val param = ApiParameter(name = "id")
        assertEquals("id", param.name)
        assertEquals(ParameterType.TEXT, param.type)
        assertFalse(param.required)
        assertNull(param.binding)
        assertNull(param.defaultValue)
        assertNull(param.description)
        assertNull(param.example)
        assertNull(param.enumValues)
    }

    @Test
    fun testFullConstruction() {
        val param = ApiParameter(
            name = "userId",
            type = ParameterType.FILE,
            required = true,
            binding = ParameterBinding.Form,
            defaultValue = "0",
            description = "User ID",
            example = "123",
            enumValues = listOf("1", "2", "3")
        )
        assertEquals("userId", param.name)
        assertEquals(ParameterType.FILE, param.type)
        assertTrue(param.required)
        assertEquals(ParameterBinding.Form, param.binding)
        assertEquals("0", param.defaultValue)
        assertEquals("User ID", param.description)
        assertEquals("123", param.example)
        assertEquals(listOf("1", "2", "3"), param.enumValues)
    }
}

class ApiHeaderPureTest {

    @Test
    fun testDefaultValues() {
        val header = ApiHeader(name = "Content-Type")
        assertEquals("Content-Type", header.name)
        assertNull(header.value)
        assertNull(header.description)
        assertNull(header.example)
        assertFalse(header.required)
    }

    @Test
    fun testFullConstruction() {
        val header = ApiHeader(
            name = "Authorization",
            value = "Bearer token",
            description = "Auth token",
            example = "Bearer abc123",
            required = true
        )
        assertEquals("Authorization", header.name)
        assertEquals("Bearer token", header.value)
        assertTrue(header.required)
    }
}

class GrpcMetadataTest {

    @Test
    fun testProtocol() {
        val meta = GrpcMetadata(
            path = "/test.Service/Method",
            serviceName = "Service",
            methodName = "Method",
            packageName = "test",
            streamingType = GrpcStreamingType.UNARY
        )
        assertEquals("gRPC", meta.protocol)
    }

    @Test
    fun testDefaultValues() {
        val meta = GrpcMetadata(
            path = "/test.Service/Method",
            serviceName = "Service",
            methodName = "Method",
            packageName = "test",
            streamingType = GrpcStreamingType.SERVER_STREAMING
        )
        assertNull(meta.protoFile)
        assertNull(meta.body)
        assertNull(meta.responseBody)
        assertNull(meta.responseType)
    }

    @Test
    fun testStreamingTypes() {
        assertEquals(4, GrpcStreamingType.values().size)
        assertEquals(GrpcStreamingType.UNARY, GrpcStreamingType.valueOf("UNARY"))
        assertEquals(GrpcStreamingType.SERVER_STREAMING, GrpcStreamingType.valueOf("SERVER_STREAMING"))
        assertEquals(GrpcStreamingType.CLIENT_STREAMING, GrpcStreamingType.valueOf("CLIENT_STREAMING"))
        assertEquals(GrpcStreamingType.BIDIRECTIONAL, GrpcStreamingType.valueOf("BIDIRECTIONAL"))
    }
}
