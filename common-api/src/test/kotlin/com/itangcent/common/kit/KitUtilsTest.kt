package com.itangcent.common.kit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Test case for [KitUtils]
 */
class KitUtilsTest {

    @Test
    fun testToJson() {
        assertEquals(null, null.toJson())
        assertEquals("str", "str".toJson())
        assertEquals("1", 1.toJson())
        assertEquals("{\"a\":\"b\"}", linkedMapOf("a" to "b").toJson())
    }

    @Test
    fun testHeadLine() {
        assertEquals(null, "".headLine())
        assertEquals("str", "str".headLine())
        assertEquals("first", "first\nsecond".headLine())
        assertEquals("first", "\nfirst\nsecond".headLine())
        assertEquals("first", "first\rsecond".headLine())
        assertEquals("first", "\rfirst\rsecond".headLine())
    }

    @Test
    fun testEqualIgnoreCase() {
        assertTrue(null.equalIgnoreCase(null))
        assertTrue("".equalIgnoreCase(""))
        assertFalse("".equalIgnoreCase(null))
        assertFalse(null.equalIgnoreCase(""))
        assertFalse("abc".equalIgnoreCase("cba"))
        assertTrue("abc".equalIgnoreCase("ABC"))
        assertTrue("cbA".equalIgnoreCase("CBa"))
    }
}
