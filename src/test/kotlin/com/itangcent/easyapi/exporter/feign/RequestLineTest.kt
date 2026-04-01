package com.itangcent.easyapi.exporter.feign

import com.itangcent.easyapi.exporter.model.HttpMethod
import org.junit.Assert.*
import org.junit.Test

class RequestLineTest {

    @Test
    fun testRequestLineCreation() {
        val requestLine = RequestLine(HttpMethod.GET, "/api/users")
        assertEquals(HttpMethod.GET, requestLine.method)
        assertEquals("/api/users", requestLine.path)
    }

    @Test
    fun testRequestLineWithPostMethod() {
        val requestLine = RequestLine(HttpMethod.POST, "/api/users")
        assertEquals(HttpMethod.POST, requestLine.method)
        assertEquals("/api/users", requestLine.path)
    }

    @Test
    fun testRequestLineWithAllHttpMethods() {
        val methods = listOf(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH)
        methods.forEach { method ->
            val requestLine = RequestLine(method, "/test")
            assertEquals(method, requestLine.method)
        }
    }

    @Test
    fun testRequestLineEquality() {
        val requestLine1 = RequestLine(HttpMethod.GET, "/api/users")
        val requestLine2 = RequestLine(HttpMethod.GET, "/api/users")
        assertEquals(requestLine1, requestLine2)
    }

    @Test
    fun testRequestLineCopy() {
        val original = RequestLine(HttpMethod.GET, "/api/users")
        val copy = original.copy(method = HttpMethod.POST)
        assertEquals(HttpMethod.POST, copy.method)
        assertEquals("/api/users", copy.path)
        assertEquals(HttpMethod.GET, original.method)
    }

    @Test
    fun testRequestLineWithPathVariables() {
        val requestLine = RequestLine(HttpMethod.GET, "/api/users/{id}")
        assertEquals("/api/users/{id}", requestLine.path)
    }

    @Test
    fun testRequestLineWithEmptyPath() {
        val requestLine = RequestLine(HttpMethod.GET, "")
        assertEquals("", requestLine.path)
    }

    @Test
    fun testRequestLineWithRootPath() {
        val requestLine = RequestLine(HttpMethod.GET, "/")
        assertEquals("/", requestLine.path)
    }
}
