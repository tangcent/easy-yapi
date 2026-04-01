package com.itangcent.easyapi.exporter.springmvc

import com.itangcent.easyapi.exporter.model.HttpMethod
import org.junit.Assert.*
import org.junit.Test

class ResolvedMappingTest {

    @Test
    fun testConstruction() {
        val mapping = ResolvedMapping(
            path = "/api/users",
            method = HttpMethod.GET,
            consumes = listOf("application/json"),
            produces = listOf("application/json"),
            headers = listOf("Authorization" to "Bearer token")
        )
        
        assertEquals("/api/users", mapping.path)
        assertEquals(HttpMethod.GET, mapping.method)
        assertEquals(1, mapping.consumes.size)
        assertEquals(1, mapping.produces.size)
        assertEquals(1, mapping.headers.size)
    }

    @Test
    fun testConstructionWithDefaults() {
        val mapping = ResolvedMapping(
            path = "/test",
            method = HttpMethod.POST
        )
        
        assertEquals("/test", mapping.path)
        assertEquals(HttpMethod.POST, mapping.method)
        assertTrue(mapping.consumes.isEmpty())
        assertTrue(mapping.produces.isEmpty())
        assertTrue(mapping.headers.isEmpty())
    }

    @Test
    fun testCopy() {
        val mapping = ResolvedMapping(path = "/test", method = HttpMethod.GET)
        val copy = mapping.copy(method = HttpMethod.PUT)
        
        assertEquals("/test", copy.path)
        assertEquals(HttpMethod.PUT, copy.method)
    }

    @Test
    fun testEquality() {
        val mapping1 = ResolvedMapping(
            path = "/api/users",
            method = HttpMethod.GET,
            consumes = listOf("application/json")
        )
        val mapping2 = ResolvedMapping(
            path = "/api/users",
            method = HttpMethod.GET,
            consumes = listOf("application/json")
        )
        
        assertEquals(mapping1, mapping2)
    }
}
