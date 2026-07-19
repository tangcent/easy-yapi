package com.itangcent.easyapi.core.extension

import org.junit.Assert.*
import org.junit.Test

class ExtensionConfigTest {

    @Test
    fun testBasicConstruction() {
        val config = ExtensionConfig(
            code = "test-code",
            description = "Test extension",
            content = "println 'hello'"
        )
        assertEquals("test-code", config.code)
        assertEquals("Test extension", config.description)
        assertEquals("println 'hello'", config.content)
        assertNull(config.onClass)
        assertFalse(config.defaultEnabled)
    }

    @Test
    fun testFullConstruction() {
        val config = ExtensionConfig(
            code = "ext1",
            description = "Extension 1",
            content = "script content",
            onClass = "com.example.Foo",
            defaultEnabled = true
        )
        assertEquals("ext1", config.code)
        assertEquals("Extension 1", config.description)
        assertEquals("script content", config.content)
        assertEquals("com.example.Foo", config.onClass)
        assertTrue(config.defaultEnabled)
    }

    @Test
    fun testEmptyConstant() {
        val empty = ExtensionConfig.EMPTY
        assertEquals("", empty.code)
        assertEquals("", empty.description)
        assertEquals("", empty.content)
        assertNull(empty.onClass)
        assertFalse(empty.defaultEnabled)
    }

    @Test
    fun testCopy() {
        val original = ExtensionConfig(
            code = "orig",
            description = "Original",
            content = "content",
            onClass = "com.Foo",
            defaultEnabled = true
        )
        val copy = original.copy(code = "modified")
        assertEquals("modified", copy.code)
        assertEquals("Original", copy.description)
        assertEquals("content", copy.content)
    }

    @Test
    fun testEquality() {
        val c1 = ExtensionConfig("a", "b", "c", null, false)
        val c2 = ExtensionConfig("a", "b", "c", null, false)
        assertEquals(c1, c2)
    }

    @Test
    fun testInequality() {
        val c1 = ExtensionConfig("a", "b", "c", null, false)
        val c2 = ExtensionConfig("x", "b", "c", null, false)
        assertNotEquals(c1, c2)
    }
}
