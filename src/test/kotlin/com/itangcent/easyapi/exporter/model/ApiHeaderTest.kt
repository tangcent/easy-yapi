package com.itangcent.easyapi.exporter.model

import org.junit.Assert.*
import org.junit.Test

class ApiHeaderTest {

    @Test
    fun testApiHeaderCreation() {
        val header = ApiHeader(
            name = "Content-Type",
            value = "application/json",
            description = "The content type",
            example = "application/json",
            required = true
        )
        assertEquals("Content-Type", header.name)
        assertEquals("application/json", header.value)
        assertEquals("The content type", header.description)
        assertEquals("application/json", header.example)
        assertTrue(header.required)
    }

    @Test
    fun testApiHeaderWithDefaults() {
        val header = ApiHeader(name = "Authorization")
        assertEquals("Authorization", header.name)
        assertNull(header.value)
        assertNull(header.description)
        assertNull(header.example)
        assertFalse(header.required)
    }

    @Test
    fun testApiHeaderEquality() {
        val header1 = ApiHeader("Content-Type", "application/json")
        val header2 = ApiHeader("Content-Type", "application/json")
        assertEquals(header1, header2)
    }

    @Test
    fun testApiHeaderCopy() {
        val original = ApiHeader("Content-Type", "application/json")
        val copy = original.copy(value = "text/plain")
        assertEquals("text/plain", copy.value)
        assertEquals("application/json", original.value)
    }

    @Test
    fun testApiHeaderComponentFunctions() {
        val header = ApiHeader(
            name = "X-Custom",
            value = "value",
            description = "desc",
            example = "example",
            required = true
        )
        val (name, value, desc, example, required) = header
        assertEquals("X-Custom", name)
        assertEquals("value", value)
        assertEquals("desc", desc)
        assertEquals("example", example)
        assertTrue(required)
    }

    @Test
    fun testApiHeaderWithNullValue() {
        val header = ApiHeader(name = "Accept", value = null)
        assertNull(header.value)
    }
}
