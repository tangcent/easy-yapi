package com.itangcent.common.kit

import com.itangcent.common.utils.KV
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
    fun testSafe() {
        assertDoesNotThrow { KitUtils.safe { throw RuntimeException() } }
        assertDoesNotThrow { KitUtils.safe(RuntimeException::class) { throw RuntimeException() } }
        assertThrows(RuntimeException::class.java) { KitUtils.safe(IllegalArgumentException::class) { throw RuntimeException() } }
        assertDoesNotThrow { KitUtils.safe(RuntimeException::class) { throw IllegalArgumentException() } }
        assertDoesNotThrow {
            KitUtils.safe(
                RuntimeException::class,
                IllegalArgumentException::class
            ) { throw IllegalArgumentException() }
        }
        assertThrows(RuntimeException::class.java) {
            KitUtils.safe(
                IllegalStateException::class,
                IllegalArgumentException::class
            ) { throw RuntimeException() }
        }

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

}
