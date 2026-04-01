package com.itangcent.easyapi.dashboard

import org.junit.Assert.*
import org.junit.Test

class RequestEditCacheTest {

    @Test
    fun testConstruction() {
        val cache = RequestEditCache(
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
        val cache = RequestEditCache()
        
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
        val cache = RequestEditCache(key = "test-key")
        assertEquals("test-key", cache.cacheKey())
    }

    @Test
    fun testCacheKeyWhenNull() {
        val cache = RequestEditCache()
        assertEquals("", cache.cacheKey())
    }

    @Test
    fun testCopy() {
        val cache = RequestEditCache(key = "test", method = "GET")
        val copy = cache.copy(method = "POST")
        
        assertEquals("test", copy.key)
        assertEquals("POST", copy.method)
    }

    @Test
    fun testEquality() {
        val cache1 = RequestEditCache(key = "test", method = "GET")
        val cache2 = RequestEditCache(key = "test", method = "GET")
        
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
