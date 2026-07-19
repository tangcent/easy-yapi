package com.itangcent.easyapi.framework.feign

import com.itangcent.easyapi.core.export.HttpMethod
import org.junit.Assert.*
import org.junit.Test

class RequestLineTest {

    @Test
    fun testCreation() {
        val requestLine = RequestLine(HttpMethod.GET, "/api/users")
        assertEquals(HttpMethod.GET, requestLine.method)
        assertEquals("/api/users", requestLine.path)
    }

    @Test
    fun testEquality() {
        val line1 = RequestLine(HttpMethod.GET, "/api/users")
        val line2 = RequestLine(HttpMethod.GET, "/api/users")
        assertEquals(line1, line2)
    }

    @Test
    fun testInequality() {
        val line1 = RequestLine(HttpMethod.GET, "/api/users")
        val line2 = RequestLine(HttpMethod.POST, "/api/users")
        assertNotEquals(line1, line2)
    }

    @Test
    fun testCopy() {
        val line = RequestLine(HttpMethod.GET, "/api/users")
        val copy = line.copy(method = HttpMethod.DELETE)
        assertEquals(HttpMethod.DELETE, copy.method)
        assertEquals("/api/users", copy.path)
        assertEquals(HttpMethod.GET, line.method)
    }

    @Test
    fun testComponentFunctions() {
        val line = RequestLine(HttpMethod.POST, "/api/items")
        val (method, path) = line
        assertEquals(HttpMethod.POST, method)
        assertEquals("/api/items", path)
    }
}
