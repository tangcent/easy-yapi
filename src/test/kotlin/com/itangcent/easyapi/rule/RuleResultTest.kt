package com.itangcent.easyapi.rule

import org.junit.Assert.*
import org.junit.Test

class RuleResultTest {

    @Test
    fun testSuccessResult() {
        val result: RuleResult<String> = RuleResult.success("hello")
        assertEquals("hello", result.result)
        assertNull(result.error)
        assertTrue(result is Success)
    }

    @Test
    fun testSuccessWithNull() {
        val result: RuleResult<String> = RuleResult.success(null)
        assertNull(result.result)
        assertNull(result.error)
        // success(null) returns NullInstance
        assertTrue(result is NullInstance)
    }

    @Test
    fun testFailureResult() {
        val error = RuntimeException("test error")
        val result: RuleResult<String> = RuleResult.failure(error)
        assertNull(result.result)
        assertEquals(error, result.error)
        assertTrue(result is Failure)
    }

    @Test
    fun testNullResult() {
        val result: RuleResult<String> = RuleResult.NULL()
        assertNull(result.result)
        assertNull(result.error)
    }

    @Test
    fun testNullResultIsSingleton() {
        val r1: RuleResult<String> = RuleResult.NULL()
        val r2: RuleResult<Int> = RuleResult.NULL()
        assertSame(r1, r2)
    }

    @Test
    fun testSuccessEquality() {
        val r1 = Success("value")
        val r2 = Success("value")
        assertEquals(r1, r2)
    }

    @Test
    fun testSuccessInequality() {
        val r1 = Success("a")
        val r2 = Success("b")
        assertNotEquals(r1, r2)
    }

    @Test
    fun testFailureEquality() {
        val err = RuntimeException("msg")
        val r1 = Failure<String>(err)
        val r2 = Failure<String>(err)
        assertEquals(r1, r2)
    }

    @Test
    fun testSuccessCopy() {
        val original = Success("original")
        val copy = original.copy(result = "copied")
        assertEquals("copied", copy.result)
        assertEquals("original", original.result)
    }

    @Test
    fun testFailureCopy() {
        val err = RuntimeException("msg")
        val original = Failure<String>(err)
        val copy = original.copy(error = RuntimeException("new"))
        assertNotEquals(err, copy.error)
    }

    @Test
    fun testSuccessComponentFunctions() {
        val result = Success("value")
        val (value) = result
        assertEquals("value", value)
        assertNull(result.error)
    }

    @Test
    fun testFailureComponentFunctions() {
        val err = RuntimeException("msg")
        val result = Failure<String>(err)
        val (error) = result
        assertEquals(err, error)
        assertNull(result.result)
    }
}

class EventRuleModeAggregateTest {

    @Test
    fun testIgnoreErrorCollectsAll() {
        kotlinx.coroutines.runBlocking {
            val mode = EventRuleMode.IGNORE_ERROR
            val results = mutableListOf<RuleResult<Unit>>()
            kotlinx.coroutines.flow.flow {
                emit(RuleResult.success(Unit))
                emit(RuleResult.failure<Unit>(RuntimeException("error")))
                emit(RuleResult.success(Unit))
            }.collect { results.add(it) }
            assertEquals(3, results.size)
            // IGNORE_ERROR just collects all
            mode.aggregate(kotlinx.coroutines.flow.flow {
                emit(RuleResult.success(Unit))
                emit(RuleResult.failure<Unit>(RuntimeException("error")))
            })
        }
    }

    @Test
    fun testThrowInErrorThrowsFirstError() {
        val mode = EventRuleMode.THROW_IN_ERROR
        val error = RuntimeException("test error")
        try {
            kotlinx.coroutines.runBlocking {
                mode.aggregate(kotlinx.coroutines.flow.flow {
                    emit(RuleResult.success(Unit))
                    emit(RuleResult.failure<Unit>(error))
                })
            }
            fail("Should have thrown")
        } catch (e: Throwable) {
            assertEquals(error, e)
        }
    }

    @Test
    fun testThrowInErrorNoError() {
        kotlinx.coroutines.runBlocking {
            val mode = EventRuleMode.THROW_IN_ERROR
            val result = mode.aggregate(kotlinx.coroutines.flow.flow {
                emit(RuleResult.success(Unit))
                emit(RuleResult.success(Unit))
            })
            assertNull(result)
        }
    }
}
