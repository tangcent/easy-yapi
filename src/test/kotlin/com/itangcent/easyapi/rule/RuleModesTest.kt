package com.itangcent.easyapi.rule

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RuleModesTest {

    private fun <T> resultFlowOf(vararg values: T?): Flow<RuleResult<T>> = flow {
        for (value in values) {
            emit(if (value == null) RuleResult.NULL() else RuleResult.success(value))
        }
    }

    @Test
    fun testStringRuleModeSingle() = runBlocking {
        val mode = StringRuleMode.SINGLE

        assertNull(mode.aggregate(resultFlowOf<String>()))
        assertNull(mode.aggregate(resultFlowOf<String>(null, null)))
        assertEquals("first", mode.aggregate(resultFlowOf("first", "second")))
        assertEquals("value", mode.aggregate(resultFlowOf<String>(null, "value")))
    }

    @Test
    fun testStringRuleModeMerge() = runBlocking {
        val mode = StringRuleMode.MERGE

        assertNull(mode.aggregate(resultFlowOf<String>()))
        assertNull(mode.aggregate(resultFlowOf<String>(null, null)))
        assertNull(mode.aggregate(resultFlowOf("", "")))
        assertEquals("first\nsecond", mode.aggregate(resultFlowOf("first", "second")))
        assertEquals("value", mode.aggregate(resultFlowOf<String>(null, "value")))
        assertEquals("a\nb\nc", mode.aggregate(resultFlowOf("a", "b", "c")))
    }

    @Test
    fun testStringRuleModeMergeDistinct() = runBlocking {
        val mode = StringRuleMode.MERGE_DISTINCT

        assertNull(mode.aggregate(resultFlowOf<String>()))
        assertNull(mode.aggregate(resultFlowOf<String>(null, null)))
        assertEquals("first\nsecond", mode.aggregate(resultFlowOf("first", "second")))
        assertEquals("value", mode.aggregate(resultFlowOf("value", "value")))
        assertEquals("a\nb", mode.aggregate(resultFlowOf("a", "b", "a", "b")))
    }

    @Test
    fun testBooleanRuleModeAny() = runBlocking {
        val mode = BooleanRuleMode.ANY

        assertFalse(mode.aggregate(resultFlowOf<Boolean>()))
        assertFalse(mode.aggregate(resultFlowOf<Boolean>(null, null)))
        assertFalse(mode.aggregate(resultFlowOf(false, false)))
        assertTrue(mode.aggregate(resultFlowOf(true, false)))
        assertTrue(mode.aggregate(resultFlowOf(false, true)))
        assertTrue(mode.aggregate(resultFlowOf(true, true)))
    }

    @Test
    fun testBooleanRuleModeAll() = runBlocking {
        val mode = BooleanRuleMode.ALL

        assertFalse(mode.aggregate(resultFlowOf<Boolean>()))
        assertFalse(mode.aggregate(resultFlowOf<Boolean>(null, null)))
        assertTrue(mode.aggregate(resultFlowOf(true, true)))
        assertFalse(mode.aggregate(resultFlowOf(true, false)))
        assertFalse(mode.aggregate(resultFlowOf(false, true)))
        assertFalse(mode.aggregate(resultFlowOf(false, false)))
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
    fun testIntRuleModeAggregate() = runBlocking {
        assertNull(IntRuleMode.aggregate(resultFlowOf<Int>()))
        assertNull(IntRuleMode.aggregate(resultFlowOf<Int>(null, null)))
        assertEquals(1, IntRuleMode.aggregate(resultFlowOf(1, 2, 3)))
        assertEquals(5, IntRuleMode.aggregate(resultFlowOf<Int>(null, 5, 10)))
    }
}
