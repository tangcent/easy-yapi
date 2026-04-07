package com.itangcent.easyapi.recommend

import org.junit.Assert.*
import org.junit.Test

class RecommendPresetTest {

    @Test
    fun testDefaultValues() {
        val preset = RecommendPreset(
            code = "spring",
            description = "Spring MVC support"
        )
        assertEquals("spring", preset.code)
        assertEquals("Spring MVC support", preset.description)
        assertEquals("", preset.content)
        assertFalse(preset.defaultEnabled)
    }

    @Test
    fun testCustomValues() {
        val preset = RecommendPreset(
            code = "spring",
            description = "Spring MVC support",
            content = "method.return.main=true",
            defaultEnabled = true
        )
        assertEquals("spring", preset.code)
        assertEquals("Spring MVC support", preset.description)
        assertEquals("method.return.main=true", preset.content)
        assertTrue(preset.defaultEnabled)
    }

    @Test
    fun testEquality() {
        val p1 = RecommendPreset("spring", "Spring MVC", "config=value", true)
        val p2 = RecommendPreset("spring", "Spring MVC", "config=value", true)
        assertEquals(p1, p2)
    }

    @Test
    fun testInequality() {
        val p1 = RecommendPreset("spring", "Spring MVC")
        val p2 = RecommendPreset("jaxrs", "JAX-RS")
        assertNotEquals(p1, p2)
    }

    @Test
    fun testHashCode() {
        val p1 = RecommendPreset("spring", "Spring MVC", "config=value", true)
        val p2 = RecommendPreset("spring", "Spring MVC", "config=value", true)
        assertEquals(p1.hashCode(), p2.hashCode())
    }

    @Test
    fun testCopy() {
        val original = RecommendPreset("spring", "Spring MVC", "config=value", true)
        val copy = original.copy(defaultEnabled = false)
        assertEquals("spring", copy.code)
        assertEquals("Spring MVC", copy.description)
        assertEquals("config=value", copy.content)
        assertFalse(copy.defaultEnabled)
    }
}
