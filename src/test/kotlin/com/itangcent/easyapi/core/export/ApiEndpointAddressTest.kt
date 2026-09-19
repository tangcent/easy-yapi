package com.itangcent.easyapi.core.export

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [address], the user-facing `METHOD /path` form of an endpoint.
 */
class ApiEndpointAddressTest {

    @Test
    fun testHttpEndpointIsMethodPlusPath() {
        val endpoint = ApiEndpoint(
            name = "getUser",
            metadata = httpMetadata(path = "/api/user/get", method = HttpMethod.GET)
        )
        assertEquals("GET /api/user/get", endpoint.address)
    }

    @Test
    fun testSamePathDifferentMethodsStayDistinguishable() {
        val get = ApiEndpoint(metadata = httpMetadata(path = "/user/{id}", method = HttpMethod.GET))
        val put = ApiEndpoint(metadata = httpMetadata(path = "/user/{id}", method = HttpMethod.PUT))

        assertEquals("GET /user/{id}", get.address)
        assertEquals("PUT /user/{id}", put.address)
    }

    @Test
    fun testEndpointWithoutExplicitMethodFallsBackToBarePath() {
        val endpoint = ApiEndpoint(metadata = httpMetadata(path = "/api/user", method = HttpMethod.NO_METHOD))
        assertEquals(
            "NO_METHOD is not a real verb and must not be printed",
            "/api/user",
            endpoint.address
        )
    }

    @Test
    fun testGrpcEndpointIsBarePath() {
        val endpoint = ApiEndpoint(
            metadata = GrpcMetadata(
                path = "/com.example.UserService/GetUser",
                serviceName = "UserService",
                methodName = "GetUser",
                packageName = "com.example",
                streamingType = GrpcStreamingType.UNARY
            )
        )
        assertEquals("/com.example.UserService/GetUser", endpoint.address)
    }
}
