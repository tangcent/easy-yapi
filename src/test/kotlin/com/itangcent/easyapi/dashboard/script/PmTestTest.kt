package com.itangcent.easyapi.dashboard.script

import org.junit.Assert.*
import org.junit.Test

class PmTestCollectorTest {

    @Test
    fun testEmptyCollector() {
        val collector = PmTestCollector()
        assertTrue(collector.results.isEmpty())
        assertEquals(0, collector.index())
    }

    @Test
    fun testAddResult() {
        val collector = PmTestCollector()
        collector.addResult("test1", true)
        collector.addResult("test2", false, "expected 200 but was 404")
        assertEquals(2, collector.results.size)
        assertTrue(collector.results[0].passed)
        assertFalse(collector.results[1].passed)
        assertEquals("expected 200 but was 404", collector.results[1].error)
    }

    @Test
    fun testIncrementIndex() {
        val collector = PmTestCollector()
        assertEquals(0, collector.incrementIndex())
        assertEquals(1, collector.index())
        assertEquals(1, collector.incrementIndex())
        assertEquals(2, collector.index())
    }

    @Test
    fun testResultsAreImmutable() {
        val collector = PmTestCollector()
        collector.addResult("test1", true)
        val results = collector.results
        assertEquals(1, results.size)
    }
}

class PmTestTest {

    @Test
    fun testPassingTest() {
        val collector = PmTestCollector()
        val pmTest = PmTest(collector)
        pmTest("should pass") {}
        assertEquals(1, collector.results.size)
        assertTrue(collector.results[0].passed)
        assertEquals("should pass", collector.results[0].name)
        assertNull(collector.results[0].error)
    }

    @Test
    fun testFailingTestWithAssertionError() {
        val collector = PmTestCollector()
        val pmTest = PmTest(collector)
        pmTest("should fail") {
            throw AssertionError("values differ")
        }
        assertEquals(1, collector.results.size)
        assertFalse(collector.results[0].passed)
        assertEquals("values differ", collector.results[0].error)
    }

    @Test
    fun testFailingTestWithException() {
        val collector = PmTestCollector()
        val pmTest = PmTest(collector)
        pmTest("should fail") {
            throw RuntimeException("something went wrong")
        }
        assertEquals(1, collector.results.size)
        assertFalse(collector.results[0].passed)
        assertEquals("something went wrong", collector.results[0].error)
    }

    @Test
    fun testMultipleTests() {
        val collector = PmTestCollector()
        val pmTest = PmTest(collector)
        pmTest("test 1") {}
        pmTest("test 2") { throw AssertionError("fail") }
        pmTest("test 3") {}
        assertEquals(3, collector.results.size)
        assertTrue(collector.results[0].passed)
        assertFalse(collector.results[1].passed)
        assertTrue(collector.results[2].passed)
    }

    @Test
    fun testSkip() {
        val collector = PmTestCollector()
        val pmTest = PmTest(collector)
        pmTest.skip("skipped test") {}
        assertEquals(1, collector.results.size)
        assertTrue(collector.results[0].passed)
        assertEquals("[SKIPPED]", collector.results[0].error)
    }

    @Test
    fun testIndexIncrementedAfterEachTest() {
        val collector = PmTestCollector()
        val pmTest = PmTest(collector)
        pmTest("test 1") {}
        assertEquals(1, collector.index())
        pmTest("test 2") {}
        assertEquals(2, collector.index())
    }
}

class TestResultTest {

    @Test
    fun testConstruction() {
        val result = TestResult("test name", true)
        assertEquals("test name", result.name)
        assertTrue(result.passed)
        assertNull(result.error)
    }

    @Test
    fun testConstructionWithError() {
        val result = TestResult("test name", false, "error msg")
        assertEquals("test name", result.name)
        assertFalse(result.passed)
        assertEquals("error msg", result.error)
    }

    @Test
    fun testEquality() {
        val r1 = TestResult("test", true)
        val r2 = TestResult("test", true)
        assertEquals(r1, r2)
    }
}
