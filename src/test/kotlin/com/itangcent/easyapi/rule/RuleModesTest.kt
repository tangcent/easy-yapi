package com.itangcent.easyapi.rule

import org.junit.Assert.*
import org.junit.Test

class RuleModesTest {

    @Test
    fun testStringRuleModeSingle() {
        val mode = StringRuleMode.SINGLE

        assertNull(mode.aggregate(emptyList()))
        assertNull(mode.aggregate(listOf(null, null)))
        assertEquals("first", mode.aggregate(listOf("first", "second")))
        assertEquals("value", mode.aggregate(listOf(null, "value")))
    }

    @Test
    fun testStringRuleModeMerge() {
        val mode = StringRuleMode.MERGE

        assertNull(mode.aggregate(emptyList()))
        assertNull(mode.aggregate(listOf(null, null)))
        assertNull(mode.aggregate(listOf("", "")))
        assertEquals("first\nsecond", mode.aggregate(listOf("first", "second")))
        assertEquals("value", mode.aggregate(listOf(null, "value")))
        assertEquals("a\nb\nc", mode.aggregate(listOf("a", "b", "c")))
    }

    @Test
    fun testStringRuleModeMergeDistinct() {
        val mode = StringRuleMode.MERGE_DISTINCT

        assertNull(mode.aggregate(emptyList()))
        assertNull(mode.aggregate(listOf(null, null)))
        assertEquals("first\nsecond", mode.aggregate(listOf("first", "second")))
        assertEquals("value", mode.aggregate(listOf("value", "value")))
        assertEquals("a\nb", mode.aggregate(listOf("a", "b", "a", "b")))
    }

    @Test
    fun testBooleanRuleModeAny() {
        val mode = BooleanRuleMode.ANY

        assertFalse(mode.aggregate(emptyList()))
        assertFalse(mode.aggregate(listOf(null, null)))
        assertFalse(mode.aggregate(listOf(false, false)))
        assertTrue(mode.aggregate(listOf(true, false)))
        assertTrue(mode.aggregate(listOf(false, true)))
        assertTrue(mode.aggregate(listOf(true, true)))
    }

    @Test
    fun testEventRuleModeIgnoreError() {
        val mode = EventRuleMode.IGNORE_ERROR
        assertFalse(mode.throwOnError)
    }

    @Test
    fun testEventRuleModeThrowInError() {
        val mode = EventRuleMode.THROW_IN_ERROR
        assertTrue(mode.throwOnError)
    }

    @Test
    fun testIntRuleModeAggregate() {
        assertNull(IntRuleMode.aggregate(emptyList()))
        assertNull(IntRuleMode.aggregate(listOf(null, null)))
        assertEquals(1, IntRuleMode.aggregate(listOf(1, 2, 3)))
        assertEquals(5, IntRuleMode.aggregate(listOf(null, 5, 10)))
    }
}
