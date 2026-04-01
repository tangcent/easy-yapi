package com.itangcent.easyapi.exporter.model

import org.junit.Assert.*
import org.junit.Test

class HttpMethodTest {

    @Test
    fun testAllHttpMethods() {
        val methods = listOf(
            HttpMethod.GET,
            HttpMethod.POST,
            HttpMethod.PUT,
            HttpMethod.DELETE,
            HttpMethod.PATCH,
            HttpMethod.HEAD,
            HttpMethod.OPTIONS,
            HttpMethod.NO_METHOD
        )
        assertEquals(8, methods.size)
        assertEquals(8, HttpMethod.entries.size)
    }

    @Test
    fun testFromSpringGet() {
        assertEquals(HttpMethod.GET, HttpMethod.fromSpring("GET"))
        assertEquals(HttpMethod.GET, HttpMethod.fromSpring("get"))
        assertEquals(HttpMethod.GET, HttpMethod.fromSpring("Get"))
    }

    @Test
    fun testFromSpringPost() {
        assertEquals(HttpMethod.POST, HttpMethod.fromSpring("POST"))
        assertEquals(HttpMethod.POST, HttpMethod.fromSpring("post"))
    }

    @Test
    fun testFromSpringPut() {
        assertEquals(HttpMethod.PUT, HttpMethod.fromSpring("PUT"))
        assertEquals(HttpMethod.PUT, HttpMethod.fromSpring("put"))
    }

    @Test
    fun testFromSpringDelete() {
        assertEquals(HttpMethod.DELETE, HttpMethod.fromSpring("DELETE"))
        assertEquals(HttpMethod.DELETE, HttpMethod.fromSpring("delete"))
    }

    @Test
    fun testFromSpringPatch() {
        assertEquals(HttpMethod.PATCH, HttpMethod.fromSpring("PATCH"))
        assertEquals(HttpMethod.PATCH, HttpMethod.fromSpring("patch"))
    }

    @Test
    fun testFromSpringHead() {
        assertEquals(HttpMethod.HEAD, HttpMethod.fromSpring("HEAD"))
        assertEquals(HttpMethod.HEAD, HttpMethod.fromSpring("head"))
    }

    @Test
    fun testFromSpringOptions() {
        assertEquals(HttpMethod.OPTIONS, HttpMethod.fromSpring("OPTIONS"))
        assertEquals(HttpMethod.OPTIONS, HttpMethod.fromSpring("options"))
    }

    @Test
    fun testFromSpringInvalid() {
        assertNull(HttpMethod.fromSpring("INVALID"))
        assertNull(HttpMethod.fromSpring(""))
        assertNull(HttpMethod.fromSpring("unknown"))
    }

    @Test
    fun testHttpMethodName() {
        assertEquals("GET", HttpMethod.GET.name)
        assertEquals("POST", HttpMethod.POST.name)
        assertEquals("PUT", HttpMethod.PUT.name)
        assertEquals("DELETE", HttpMethod.DELETE.name)
        assertEquals("PATCH", HttpMethod.PATCH.name)
        assertEquals("HEAD", HttpMethod.HEAD.name)
        assertEquals("OPTIONS", HttpMethod.OPTIONS.name)
        assertEquals("NO_METHOD", HttpMethod.NO_METHOD.name)
    }

    @Test
    fun testHttpMethodOrdinal() {
        assertEquals(0, HttpMethod.GET.ordinal)
        assertEquals(1, HttpMethod.POST.ordinal)
        assertEquals(2, HttpMethod.PUT.ordinal)
        assertEquals(3, HttpMethod.DELETE.ordinal)
        assertEquals(4, HttpMethod.PATCH.ordinal)
    }
}
