package com.itangcent.easyapi.psi

import org.junit.Assert.*
import org.junit.Test

class ElementCounterTest {

    @Test
    fun testInitialCountIsZero() {
        val counter = ElementCounter()
        assertEquals(0, counter.count())
        assertFalse(counter.isExceeded())
    }

    @Test
    fun testIncrementAndCheckNotExceeded() {
        val counter = ElementCounter(maxElements = 5)
        assertFalse(counter.incrementAndCheckExceeded())
        assertEquals(1, counter.count())
        assertFalse(counter.isExceeded())
    }

    @Test
    fun testIncrementUntilExceeded() {
        val counter = ElementCounter(maxElements = 3)
        assertFalse(counter.incrementAndCheckExceeded()) // 1
        assertFalse(counter.incrementAndCheckExceeded()) // 2
        assertFalse(counter.incrementAndCheckExceeded()) // 3
        assertTrue(counter.incrementAndCheckExceeded())  // 4 > 3
        assertTrue(counter.isExceeded())
    }

    @Test
    fun testIsExceededAtExactLimit() {
        val counter = ElementCounter(maxElements = 3)
        counter.incrementAndCheckExceeded() // 1
        counter.incrementAndCheckExceeded() // 2
        counter.incrementAndCheckExceeded() // 3
        assertFalse(counter.isExceeded()) // 3 == 3, not exceeded
    }

    @Test
    fun testCountTracksAccumulated() {
        val counter = ElementCounter(maxElements = 100)
        for (i in 1..10) {
            counter.incrementAndCheckExceeded()
        }
        assertEquals(10, counter.count())
    }

    @Test
    fun testDefaultMaxElements() {
        val counter = ElementCounter()
        for (i in 1..512) {
            assertFalse(counter.incrementAndCheckExceeded())
        }
        assertTrue(counter.incrementAndCheckExceeded()) // 513 > 512
    }

    @Test
    fun testZeroMaxElements() {
        val counter = ElementCounter(maxElements = 0)
        assertTrue(counter.incrementAndCheckExceeded()) // 1 > 0
    }
}
