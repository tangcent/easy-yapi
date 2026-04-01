package com.itangcent.easyapi.config.model

import com.itangcent.easyapi.config.parser.DirectiveSnapshot
import com.itangcent.easyapi.config.parser.ResolveMultiMode
import org.junit.Assert.*
import org.junit.Test

class ConfigEntryTest {

    @Test
    fun testBasicConfigEntry() {
        val entry = ConfigEntry("api.name", "Test API", "test-source")
        assertEquals("api.name", entry.key)
        assertEquals("Test API", entry.value)
        assertEquals("test-source", entry.sourceId)
    }

    @Test
    fun testConfigEntryWithDirectives() {
        val directives = DirectiveSnapshot(
            resolveProperty = false,
            resolveMulti = ResolveMultiMode.LAST,
            ignoreUnresolved = true
        )
        val entry = ConfigEntry("api.key", "value", "source", directives)
        assertFalse(entry.directives.resolveProperty)
        assertEquals(ResolveMultiMode.LAST, entry.directives.resolveMulti)
        assertTrue(entry.directives.ignoreUnresolved)
    }

    @Test
    fun testDefaultDirectives() {
        val entry = ConfigEntry("key", "value", "source")
        assertTrue(entry.directives.resolveProperty)
        assertEquals(ResolveMultiMode.FIRST, entry.directives.resolveMulti)
        assertFalse(entry.directives.ignoreUnresolved)
    }

    @Test
    fun testCopy() {
        val original = ConfigEntry("key", "value", "source")
        val copy = original.copy(value = "new-value")
        assertEquals("key", copy.key)
        assertEquals("new-value", copy.value)
        assertEquals("source", copy.sourceId)
    }

    @Test
    fun testEquality() {
        val entry1 = ConfigEntry("key", "value", "source")
        val entry2 = ConfigEntry("key", "value", "source")
        assertEquals(entry1, entry2)
    }

    @Test
    fun testInequality() {
        val entry1 = ConfigEntry("key1", "value", "source")
        val entry2 = ConfigEntry("key2", "value", "source")
        assertNotEquals(entry1, entry2)
    }

    @Test
    fun testToString() {
        val entry = ConfigEntry("api.name", "Test", "source")
        val str = entry.toString()
        assertTrue(str.contains("api.name"))
        assertTrue(str.contains("Test"))
    }
}
