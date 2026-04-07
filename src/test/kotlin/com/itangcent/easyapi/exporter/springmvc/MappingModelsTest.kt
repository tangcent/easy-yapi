package com.itangcent.easyapi.exporter.springmvc

import com.itangcent.easyapi.exporter.model.HttpMethod
import org.junit.Assert.*
import org.junit.Test

class MappingModelsTest {

    @Test
    fun testResolvedMapping_defaults() {
        val mapping = ResolvedMapping(path = "/api/users", method = HttpMethod.GET)
        assertEquals("/api/users", mapping.path)
        assertEquals(HttpMethod.GET, mapping.method)
        assertTrue(mapping.consumes.isEmpty())
        assertTrue(mapping.produces.isEmpty())
        assertTrue(mapping.headers.isEmpty())
    }

    @Test
    fun testResolvedMapping_withAll() {
        val mapping = ResolvedMapping(
            path = "/api/users",
            method = HttpMethod.POST,
            consumes = listOf("application/json"),
            produces = listOf("application/json"),
            headers = listOf("X-Api-Key" to "required")
        )
        assertEquals(HttpMethod.POST, mapping.method)
        assertEquals(listOf("application/json"), mapping.consumes)
        assertEquals(listOf("application/json"), mapping.produces)
        assertEquals(1, mapping.headers.size)
        assertEquals("X-Api-Key", mapping.headers[0].first)
    }

    @Test
    fun testResolvedMapping_equality() {
        val a = ResolvedMapping("/a", HttpMethod.GET)
        val b = ResolvedMapping("/a", HttpMethod.GET)
        assertEquals(a, b)
    }

    @Test
    fun testMappingInfo_empty() {
        val info = MappingInfo.EMPTY
        assertTrue(info.paths.isEmpty())
        assertTrue(info.methods.isEmpty())
        assertTrue(info.consumes.isEmpty())
        assertTrue(info.produces.isEmpty())
        assertTrue(info.headers.isEmpty())
    }

    @Test
    fun testMappingInfo_withValues() {
        val info = MappingInfo(
            paths = listOf("/users", "/api/users"),
            methods = listOf(HttpMethod.GET, HttpMethod.POST),
            consumes = listOf("application/json"),
            produces = listOf("text/plain"),
            headers = listOf("Accept" to "application/json")
        )
        assertEquals(2, info.paths.size)
        assertEquals(2, info.methods.size)
        assertEquals(1, info.consumes.size)
    }

    @Test
    fun testMappingInfo_equality() {
        val a = MappingInfo(paths = listOf("/a"))
        val b = MappingInfo(paths = listOf("/a"))
        assertEquals(a, b)
    }

    @Test
    fun testMappingInfo_copy() {
        val info = MappingInfo(paths = listOf("/a"))
        val copy = info.copy(methods = listOf(HttpMethod.PUT))
        assertEquals(listOf("/a"), copy.paths)
        assertEquals(listOf(HttpMethod.PUT), copy.methods)
    }
}
