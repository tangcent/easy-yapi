package com.itangcent.easyapi.core.dashboard

import com.itangcent.easyapi.core.export.*
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.*

/**
 * Extended tests for RequestEditCacheService covering:
 * - Blank key handling (save/load/delete with blank keys)
 * - gRPC cache operations
 * - createDefaultCache with parameters
 */
class RequestEditCacheServiceExtendedTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var service: RequestEditCacheService

    override fun setUp() {
        super.setUp()
        service = RequestEditCacheService.getInstance(project)
    }

    // ── Blank key handling ───────────────────────────────────────────────────

    fun testSaveWithBlankKeyDoesNothing() {
        val endpoint = ApiEndpoint(
            name = "Test",
            metadata = HttpMetadata(method = HttpMethod.GET, path = "/api/test")
        )
        val cache = HttpRequestEditCache(key = "", name = "Test", path = "/api/test", method = "GET")
        // Should not throw and should not persist
        service.save(endpoint, cache, "")
        val loaded = service.load(endpoint, "")
        assertNull("Should return null for blank key", loaded)
    }

    fun testLoadWithBlankKeyReturnsNull() {
        val endpoint = ApiEndpoint(
            name = "Test",
            metadata = HttpMetadata(method = HttpMethod.GET, path = "/api/test")
        )
        assertNull("Should return null for blank key", service.load(endpoint, ""))
    }

    fun testDeleteWithBlankKeyDoesNothing() {
        // Should not throw
        service.delete("", false)
        service.delete("", true)
    }

    // ── gRPC cache operations ────────────────────────────────────────────────

    fun testSaveAndLoadGrpcCache() {
        val endpoint = ApiEndpoint(
            name = "GetUser",
            metadata = GrpcMetadata(
                serviceName = "UserService",
                methodName = "GetUser",
                packageName = "com.example",
                streamingType = GrpcStreamingType.UNARY,
                path = "/com.example.UserService/GetUser"
            )
        )
        val key = "grpc-test-save-load"
        val cache = GrpcRequestEditCache(
            key = key,
            name = "GetUser",
            serviceName = "UserService",
            methodName = "GetUser",
            host = "localhost:9090",
            body = """{"id": "1"}"""
        )

        service.save(endpoint, cache, key)
        val loaded = service.load(endpoint, key)

        assertNotNull("Should load saved gRPC cache", loaded)
        assertTrue("Loaded cache should be GrpcRequestEditCache", loaded is GrpcRequestEditCache)
        val loadedGrpc = loaded as GrpcRequestEditCache
        assertEquals("UserService", loadedGrpc.serviceName)
        assertEquals("GetUser", loadedGrpc.methodName)
        assertEquals("localhost:9090", loadedGrpc.host)
    }

    fun testDeleteGrpcCache() {
        val endpoint = ApiEndpoint(
            name = "GetUser",
            metadata = GrpcMetadata(
                serviceName = "UserService",
                methodName = "GetUser",
                packageName = "com.example",
                streamingType = GrpcStreamingType.UNARY,
                path = "/UserService/GetUser"
            )
        )
        val key = "grpc-test-delete"
        val cache = GrpcRequestEditCache(key = key, name = "GetUser")

        service.save(endpoint, cache, key)
        service.delete(key, true)
        val loaded = service.load(endpoint, key)

        assertNull("Should return null after deleting gRPC cache", loaded)
    }

    // ── createDefaultCache for HTTP ──────────────────────────────────────────

    fun testCreateDefaultHttpCacheWithParameters() {
        val endpoint = ApiEndpoint(
            name = "Create User",
            metadata = HttpMetadata(
                method = HttpMethod.POST,
                path = "/api/users/{id}",
                parameters = mutableListOf(
                    ApiParameter(name = "id", binding = ParameterBinding.Path, defaultValue = "1"),
                    ApiParameter(name = "page", binding = ParameterBinding.Query, defaultValue = "1"),
                    ApiParameter(name = "X-Auth", binding = ParameterBinding.Header, defaultValue = "token123"),
                    ApiParameter(name = "file", binding = ParameterBinding.Form, defaultValue = "data")
                ),
                headers = mutableListOf(
                    ApiHeader(name = "Content-Type", value = "application/json")
                ),
                contentType = "application/json"
            )
        )

        val cache = service.createDefaultCache(endpoint, "test-http-params", "https://api.example.com")

        assertNotNull(cache)
        assertTrue(cache is HttpRequestEditCache)
        val httpCache = cache as HttpRequestEditCache
        assertEquals("Create User", httpCache.name)
        assertEquals("/api/users/{id}", httpCache.path)
        assertEquals("POST", httpCache.method)
        assertEquals("https://api.example.com", httpCache.host)
        assertEquals("application/json", httpCache.contentType)
        // Should have path params
        assertTrue(httpCache.pathParams.any { it.name == "id" })
        // Should have query params
        assertTrue(httpCache.queryParams.any { it.name == "page" })
        // Should have headers (both from metadata headers and header params)
        assertTrue(httpCache.headers.any { it.name == "Content-Type" || it.name == "X-Auth" })
        // Should have form params
        assertTrue(httpCache.formParams.any { it.name == "file" })
    }

    fun testCreateDefaultHttpCacheWithHost() {
        val endpoint = ApiEndpoint(
            name = "Get Users",
            metadata = HttpMetadata(method = HttpMethod.GET, path = "/api/users")
        )

        val cache = service.createDefaultCache(endpoint, "test-http-host", "https://custom.host.com")

        assertTrue(cache is HttpRequestEditCache)
        val httpCache = cache as HttpRequestEditCache
        assertEquals("https://custom.host.com", httpCache.host)
    }

    fun testCreateDefaultHttpCacheWithoutHost() {
        val endpoint = ApiEndpoint(
            name = "Get Users",
            metadata = HttpMetadata(method = HttpMethod.GET, path = "/api/users")
        )

        val cache = service.createDefaultCache(endpoint, "test-http-no-host")

        assertTrue(cache is HttpRequestEditCache)
        val httpCache = cache as HttpRequestEditCache
        assertNull(httpCache.host)
    }

    // ── createDefaultCache for gRPC ──────────────────────────────────────────

    fun testCreateDefaultGrpcCache() {
        val endpoint = ApiEndpoint(
            name = "GetUser",
            metadata = GrpcMetadata(
                serviceName = "UserService",
                methodName = "GetUser",
                packageName = "com.example.api",
                streamingType = GrpcStreamingType.UNARY,
                path = "/com.example.api.UserService/GetUser"
            )
        )

        val cache = service.createDefaultCache(endpoint, "test-grpc-default", "localhost:9090")

        assertNotNull(cache)
        assertTrue(cache is GrpcRequestEditCache)
        val grpcCache = cache as GrpcRequestEditCache
        assertEquals("GetUser", grpcCache.name)
        assertEquals("UserService", grpcCache.serviceName)
        assertEquals("GetUser", grpcCache.methodName)
        assertEquals("com.example.api", grpcCache.packageName)
        assertEquals("localhost:9090", grpcCache.host)
    }

    fun testCreateDefaultGrpcCacheWithoutHost() {
        val endpoint = ApiEndpoint(
            name = "GetUser",
            metadata = GrpcMetadata(
                serviceName = "UserService",
                methodName = "GetUser",
                packageName = "com.example",
                streamingType = GrpcStreamingType.UNARY,
                path = "/UserService/GetUser"
            )
        )

        val cache = service.createDefaultCache(endpoint, "test-grpc-no-host")

        assertTrue(cache is GrpcRequestEditCache)
        val grpcCache = cache as GrpcRequestEditCache
        assertNull(grpcCache.host)
    }

    // ── Cookie parameters treated as query params ────────────────────────────

    fun testCookieParametersInQueryParams() {
        val endpoint = ApiEndpoint(
            name = "Get Data",
            metadata = HttpMetadata(
                method = HttpMethod.GET,
                path = "/api/data",
                parameters = mutableListOf(
                    ApiParameter(name = "session", binding = ParameterBinding.Cookie, defaultValue = "abc")
                )
            )
        )

        val cache = service.createDefaultCache(endpoint, "test-cookie-params")

        assertTrue(cache is HttpRequestEditCache)
        val httpCache = cache as HttpRequestEditCache
        assertTrue(httpCache.queryParams.any { it.name == "session" })
    }

    // ── Endpoint with no metadata ────────────────────────────────────────────

    fun testCreateDefaultCacheForEndpointWithMinimalMetadata() {
        val endpoint = ApiEndpoint(
            name = "Minimal",
            metadata = HttpMetadata(method = HttpMethod.GET, path = "/minimal")
        )

        val cache = service.createDefaultCache(endpoint, "test-minimal")

        assertTrue(cache is HttpRequestEditCache)
        val httpCache = cache as HttpRequestEditCache
        assertEquals("Minimal", httpCache.name)
        assertEquals("/minimal", httpCache.path)
        assertEquals("GET", httpCache.method)
        assertTrue(httpCache.headers.isEmpty())
        assertTrue(httpCache.pathParams.isEmpty())
        assertTrue(httpCache.queryParams.isEmpty())
        assertTrue(httpCache.formParams.isEmpty())
    }

    // ── Parameter with example instead of defaultValue ───────────────────────

    fun testParameterWithExampleValue() {
        val endpoint = ApiEndpoint(
            name = "Search",
            metadata = HttpMetadata(
                method = HttpMethod.GET,
                path = "/search",
                parameters = mutableListOf(
                    ApiParameter(name = "q", binding = ParameterBinding.Query, example = "test-query")
                )
            )
        )

        val cache = service.createDefaultCache(endpoint, "test-example-param")

        assertTrue(cache is HttpRequestEditCache)
        val httpCache = cache as HttpRequestEditCache
        val queryParam = httpCache.queryParams.find { it.name == "q" }
        assertNotNull(queryParam)
        assertEquals("test-query", queryParam!!.value)
    }
}
