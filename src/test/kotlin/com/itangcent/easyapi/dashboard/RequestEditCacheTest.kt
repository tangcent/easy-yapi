package com.itangcent.easyapi.dashboard

import org.junit.Assert.*
import org.junit.Test

class HttpRequestEditCacheTest {

    @Test
    fun testConstruction() {
        val cache = HttpRequestEditCache(
            key = "user-get",
            name = "Get User",
            path = "/api/users/{id}",
            method = "GET",
            host = "http://localhost:8080",
            headers = listOf(EditableKeyValue("Authorization", "Bearer token")),
            pathParams = listOf(EditableKeyValue("id", "1")),
            queryParams = listOf(EditableKeyValue("fields", "name,email")),
            formParams = emptyList(),
            body = null,
            contentType = "application/json"
        )
        
        assertEquals("user-get", cache.key)
        assertEquals("Get User", cache.name)
        assertEquals("/api/users/{id}", cache.path)
        assertEquals("GET", cache.method)
        assertEquals("http://localhost:8080", cache.host)
        assertEquals(1, cache.headers.size)
        assertEquals(1, cache.pathParams.size)
        assertEquals(1, cache.queryParams.size)
        assertNull(cache.body)
        assertEquals("application/json", cache.contentType)
    }

    @Test
    fun testConstructionWithDefaults() {
        val cache = HttpRequestEditCache()
        
        assertNull(cache.key)
        assertNull(cache.name)
        assertNull(cache.path)
        assertNull(cache.method)
        assertNull(cache.host)
        assertTrue(cache.headers.isEmpty())
        assertTrue(cache.pathParams.isEmpty())
        assertTrue(cache.queryParams.isEmpty())
        assertTrue(cache.formParams.isEmpty())
        assertNull(cache.body)
        assertNull(cache.contentType)
    }

    @Test
    fun testCacheKey() {
        val cache = HttpRequestEditCache(key = "test-key")
        assertEquals("test-key", cache.cacheKey())
    }

    @Test
    fun testCacheKeyWhenNull() {
        val cache = HttpRequestEditCache()
        assertEquals("", cache.cacheKey())
    }

    @Test
    fun testCopy() {
        val cache = HttpRequestEditCache(key = "test", method = "GET")
        val copy = cache.copy(method = "POST")
        
        assertEquals("test", copy.key)
        assertEquals("POST", copy.method)
    }

    @Test
    fun testEquality() {
        val cache1 = HttpRequestEditCache(key = "test", method = "GET")
        val cache2 = HttpRequestEditCache(key = "test", method = "GET")
        
        assertEquals(cache1, cache2)
    }
}

class GrpcRequestEditCacheTest {

    @Test
    fun testConstruction() {
        val cache = GrpcRequestEditCache(
            key = "grpc-user-get",
            name = "Get User",
            host = "localhost:50051",
            serviceName = "UserService",
            methodName = "GetUser",
            packageName = "com.example.api",
            body = """{"id": "1"}"""
        )
        
        assertEquals("grpc-user-get", cache.key)
        assertEquals("Get User", cache.name)
        assertEquals("localhost:50051", cache.host)
        assertEquals("UserService", cache.serviceName)
        assertEquals("GetUser", cache.methodName)
        assertEquals("com.example.api", cache.packageName)
        assertEquals("""{"id": "1"}""", cache.body)
    }

    @Test
    fun testConstructionWithDefaults() {
        val cache = GrpcRequestEditCache()
        
        assertNull(cache.key)
        assertNull(cache.name)
        assertNull(cache.host)
        assertNull(cache.serviceName)
        assertNull(cache.methodName)
        assertNull(cache.packageName)
        assertNull(cache.body)
    }

    @Test
    fun testCacheKey() {
        val cache = GrpcRequestEditCache(key = "test-key")
        assertEquals("test-key", cache.cacheKey())
    }

    @Test
    fun testCacheKeyWhenNull() {
        val cache = GrpcRequestEditCache()
        assertEquals("", cache.cacheKey())
    }

    @Test
    fun testCopy() {
        val cache = GrpcRequestEditCache(key = "test", serviceName = "TestService")
        val copy = cache.copy(serviceName = "NewService")
        
        assertEquals("test", copy.key)
        assertEquals("NewService", copy.serviceName)
    }

    @Test
    fun testEquality() {
        val cache1 = GrpcRequestEditCache(key = "test", serviceName = "TestService")
        val cache2 = GrpcRequestEditCache(key = "test", serviceName = "TestService")
        
        assertEquals(cache1, cache2)
    }
}

class EditableKeyValueTest {

    @Test
    fun testConstruction() {
        val kv = EditableKeyValue(
            name = "Authorization",
            value = "Bearer token",
            description = "Authentication header"
        )
        
        assertEquals("Authorization", kv.name)
        assertEquals("Bearer token", kv.value)
        assertEquals("Authentication header", kv.description)
    }

    @Test
    fun testConstructionWithDefaults() {
        val kv = EditableKeyValue(name = "Content-Type")
        
        assertEquals("Content-Type", kv.name)
        assertNull(kv.value)
        assertNull(kv.description)
    }

    @Test
    fun testCopy() {
        val kv = EditableKeyValue(name = "test", value = "value")
        val copy = kv.copy(value = "new value")
        
        assertEquals("test", copy.name)
        assertEquals("new value", copy.value)
    }

    @Test
    fun testEquality() {
        val kv1 = EditableKeyValue(name = "test", value = "value")
        val kv2 = EditableKeyValue(name = "test", value = "value")
        
        assertEquals(kv1, kv2)
    }
}
