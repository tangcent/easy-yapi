package com.itangcent.easyapi.util

import org.junit.Assert.*
import org.junit.Test

class AnyKitTest {

    @Test
    fun testTrueValuesArray() {
        assertTrue("true" in TRUE_VALUES)
        assertTrue("1" in TRUE_VALUES)
        assertTrue("yes" in TRUE_VALUES)
        assertTrue("y" in TRUE_VALUES)
        assertTrue("on" in TRUE_VALUES)
        assertEquals(5, TRUE_VALUES.size)
    }

    @Test
    fun testFalseValuesArray() {
        assertTrue("false" in FALSE_VALUES)
        assertTrue("0" in FALSE_VALUES)
        assertTrue("no" in FALSE_VALUES)
        assertTrue("n" in FALSE_VALUES)
        assertTrue("off" in FALSE_VALUES)
        assertEquals(5, FALSE_VALUES.size)
    }

    @Test
    fun testStringAsInt_null() {
        assertNull(null.asInt())
    }

    @Test
    fun testStringAsInt_valid() {
        assertEquals(42, "42".asInt())
        assertEquals(-1, "-1".asInt())
        assertEquals(0, "0".asInt())
    }

    @Test
    fun testStringAsInt_invalid() {
        assertNull("not a number".asInt())
        assertNull("".asInt())
        assertNull("  ".asInt())
    }

    @Test
    fun testStringAsInt_withWhitespace() {
        assertEquals(42, "  42  ".asInt())
    }

    @Test
    fun testStringAsBooleanOrNull_null() {
        assertNull(null.asBooleanOrNull())
    }

    @Test
    fun testStringAsBooleanOrNull_trueValues() {
        assertEquals(true, "true".asBooleanOrNull())
        assertEquals(true, "TRUE".asBooleanOrNull())
        assertEquals(true, "True".asBooleanOrNull())
        assertEquals(true, "1".asBooleanOrNull())
        assertEquals(true, "yes".asBooleanOrNull())
        assertEquals(true, "YES".asBooleanOrNull())
        assertEquals(true, "y".asBooleanOrNull())
        assertEquals(true, "Y".asBooleanOrNull())
        assertEquals(true, "on".asBooleanOrNull())
        assertEquals(true, "ON".asBooleanOrNull())
    }

    @Test
    fun testStringAsBooleanOrNull_falseValues() {
        assertEquals(false, "false".asBooleanOrNull())
        assertEquals(false, "FALSE".asBooleanOrNull())
        assertEquals(false, "False".asBooleanOrNull())
        assertEquals(false, "0".asBooleanOrNull())
        assertEquals(false, "no".asBooleanOrNull())
        assertEquals(false, "NO".asBooleanOrNull())
        assertEquals(false, "n".asBooleanOrNull())
        assertEquals(false, "N".asBooleanOrNull())
        assertEquals(false, "off".asBooleanOrNull())
        assertEquals(false, "OFF".asBooleanOrNull())
    }

    @Test
    fun testStringAsBooleanOrNull_invalid() {
        assertNull("maybe".asBooleanOrNull())
        assertNull("".asBooleanOrNull())
        assertNull("  ".asBooleanOrNull())
    }

    @Test
    fun testStringAsBooleanOrNull_withWhitespace() {
        assertEquals(true, "  true  ".asBooleanOrNull())
        assertEquals(true, "  1  ".asBooleanOrNull())
        assertEquals(false, "  false  ".asBooleanOrNull())
        assertEquals(false, "  0  ".asBooleanOrNull())
    }

    @Test
    fun testStringAsBoolean_defaultValue() {
        assertFalse("maybe".asBoolean())
        assertFalse("".asBoolean())
        assertTrue("maybe".asBoolean(true))
        assertTrue("".asBoolean(true))
    }

    @Test
    fun testStringAsBoolean_validValues() {
        assertTrue("true".asBoolean())
        assertTrue("1".asBoolean())
        assertFalse("false".asBoolean())
        assertFalse("0".asBoolean())
    }

    @Test
    fun testAnyAsInt_null() {
        assertNull(null.asInt())
    }

    @Test
    fun testAnyAsInt_int() {
        assertEquals(42, 42.asInt())
        assertEquals(-1, (-1).asInt())
        assertEquals(0, 0.asInt())
    }

    @Test
    fun testAnyAsInt_number() {
        assertEquals(42, 42L.asInt())
        assertEquals(42, 42.0.asInt())
        assertEquals(42, 42.5.asInt())
        assertEquals(42, 42.0f.asInt())
        assertEquals(-1, (-1.5).asInt())
    }

    @Test
    fun testAnyAsInt_string() {
        assertEquals(42, "42".asInt())
        assertEquals(-1, "-1".asInt())
        assertEquals(0, "0".asInt())
        assertNull("not a number".asInt())
        assertNull("".asInt())
        assertEquals(42, "  42  ".asInt())
    }

    @Test
    fun testAnyAsInt_boolean() {
        assertEquals(1, true.asInt())
        assertEquals(0, false.asInt())
    }

    @Test
    fun testAnyAsInt_otherTypes() {
        assertEquals(42, AnyKitTestObject(42).asInt())
        assertNull(AnyKitTestObject("not a number").asInt())
    }

    @Test
    fun testAnyAsBooleanOrNull_null() {
        assertNull(null.asBooleanOrNull())
    }

    @Test
    fun testAnyAsBooleanOrNull_boolean() {
        assertEquals(true, true.asBooleanOrNull())
        assertEquals(false, false.asBooleanOrNull())
    }

    @Test
    fun testAnyAsBooleanOrNull_number() {
        assertEquals(true, 1.asBooleanOrNull())
        assertEquals(true, 42.asBooleanOrNull())
        assertEquals(true, (-1).asBooleanOrNull())
        assertEquals(false, 0.asBooleanOrNull())
        assertEquals(false, 0.0.asBooleanOrNull())
        assertEquals(true, 0.5.asBooleanOrNull())
    }

    @Test
    fun testAnyAsBooleanOrNull_string() {
        assertEquals(true, "true".asBooleanOrNull())
        assertEquals(true, "1".asBooleanOrNull())
        assertEquals(true, "yes".asBooleanOrNull())
        assertEquals(false, "false".asBooleanOrNull())
        assertEquals(false, "0".asBooleanOrNull())
        assertEquals(false, "no".asBooleanOrNull())
        assertNull("maybe".asBooleanOrNull())
    }

    @Test
    fun testAnyAsBooleanOrNull_otherTypes() {
        assertEquals(true, AnyKitTestObject("true").asBooleanOrNull())
        assertEquals(false, AnyKitTestObject("false").asBooleanOrNull())
        assertNull(AnyKitTestObject("maybe").asBooleanOrNull())
    }

    @Test
    fun testAnyAsBoolean_defaultValue() {
        assertFalse("maybe".asBoolean())
        assertFalse(AnyKitTestObject("maybe").asBoolean())
        assertTrue("maybe".asBoolean(true))
        assertTrue(AnyKitTestObject("maybe").asBoolean(true))
    }

    @Test
    fun testAnyAsBoolean_validValues() {
        assertTrue("true".asBoolean())
        assertTrue(1.asBoolean())
        assertFalse("false".asBoolean())
        assertFalse(0.asBoolean())
    }

    private data class AnyKitTestObject(private val value: Any) {
        override fun toString(): String = value.toString()
    }
}
