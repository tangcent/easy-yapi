package com.itangcent.easyapi.settings

import org.junit.Assert.*
import org.junit.Test

class MarkdownFormatTypeTest {

    @Test
    fun testValues() {
        val values = MarkdownFormatType.values()
        assertEquals(2, values.size)
        assertTrue(values.contains(MarkdownFormatType.SIMPLE))
        assertTrue(values.contains(MarkdownFormatType.ULTIMATE))
    }

    @Test
    fun testDesc() {
        assertEquals("simple columns, include name、type、desc", MarkdownFormatType.SIMPLE.desc)
        assertEquals("more columns than simple, include name、type、required、default、desc", MarkdownFormatType.ULTIMATE.desc)
    }

    @Test
    fun testName() {
        assertEquals("SIMPLE", MarkdownFormatType.SIMPLE.name)
        assertEquals("ULTIMATE", MarkdownFormatType.ULTIMATE.name)
    }

    @Test
    fun testValueOf() {
        assertEquals(MarkdownFormatType.SIMPLE, MarkdownFormatType.valueOf("SIMPLE"))
        assertEquals(MarkdownFormatType.ULTIMATE, MarkdownFormatType.valueOf("ULTIMATE"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testValueOf_invalid() {
        MarkdownFormatType.valueOf("INVALID")
    }
}
