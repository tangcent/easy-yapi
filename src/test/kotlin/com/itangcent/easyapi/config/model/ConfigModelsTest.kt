package com.itangcent.easyapi.config.model

import com.itangcent.easyapi.config.parser.DirectiveSnapshot
import com.itangcent.easyapi.config.parser.ResolveMultiMode
import org.junit.Assert.*
import org.junit.Test

class ConfigModelsTest {

    @Test
    fun testConfigEntry_defaults() {
        val entry = ConfigEntry(key = "api.name", value = "test", sourceId = "builtin")
        assertEquals("api.name", entry.key)
        assertEquals("test", entry.value)
        assertEquals("builtin", entry.sourceId)
        assertTrue(entry.directives.resolveProperty)
        assertEquals(ResolveMultiMode.FIRST, entry.directives.resolveMulti)
    }

    @Test
    fun testConfigEntry_withDirectives() {
        val directives = DirectiveSnapshot(resolveProperty = false, resolveMulti = ResolveMultiMode.LAST, ignoreUnresolved = true)
        val entry = ConfigEntry(key = "k", value = "v", sourceId = "src", directives = directives)
        assertFalse(entry.directives.resolveProperty)
        assertEquals(ResolveMultiMode.LAST, entry.directives.resolveMulti)
        assertTrue(entry.directives.ignoreUnresolved)
    }

    @Test
    fun testConfigEntry_equality() {
        val a = ConfigEntry("k", "v", "s")
        val b = ConfigEntry("k", "v", "s")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun testConfigEntry_inequality() {
        val a = ConfigEntry("k1", "v", "s")
        val b = ConfigEntry("k2", "v", "s")
        assertNotEquals(a, b)
    }

    @Test
    fun testConfigEntry_copy() {
        val entry = ConfigEntry("k", "v", "s")
        val copy = entry.copy(value = "v2")
        assertEquals("v2", copy.value)
        assertEquals("k", copy.key)
    }
}
