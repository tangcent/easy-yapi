package com.itangcent.common.kit

import com.itangcent.common.utils.KV
import com.itangcent.common.utils.safe
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

/**
 * Test case for [KitUtils]
 */
class KitUtilsTest {

    @Test
    fun testFromBool() {
        assertEquals("true", KitUtils.fromBool(true, "true", "false"))
        assertEquals("false", KitUtils.fromBool(false, "true", "false"))
    }

    @Test
    fun testToJson() {
        assertEquals(null, null.toJson())
        assertEquals("str", "str".toJson())
        assertEquals("1", 1.toJson())
        assertEquals("{\"a\":\"b\"}", KV.by("a", "b").toJson())
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

    @Test
    fun testBoolOr() {
        assertEquals("true", true.or("true", "false"))
        assertEquals("false", false.or("true", "false"))
    }

}
