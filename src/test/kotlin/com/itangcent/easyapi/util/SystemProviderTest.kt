package com.itangcent.easyapi.util

import org.junit.Assert.*
import org.junit.Test

class SystemProviderTest {

    @Test
    fun testCurrentTimeMillis_returnsReasonableValue() {
        val provider = SystemProvider()
        val time = provider.currentTimeMillis()
        assertTrue(time > 0)
        // Should be close to System.currentTimeMillis()
        val diff = Math.abs(System.currentTimeMillis() - time)
        assertTrue("Time difference should be < 1000ms", diff < 1000)
    }

    @Test
    fun testCurrentTimeMillis_canBeOverridden() {
        val mockProvider = object : SystemProvider() {
            override fun currentTimeMillis(): Long = 1234567890L
        }
        assertEquals(1234567890L, mockProvider.currentTimeMillis())
    }

    @Test
    fun testCurrentTimeMillis_monotonic() {
        val provider = SystemProvider()
        val t1 = provider.currentTimeMillis()
        val t2 = provider.currentTimeMillis()
        assertTrue(t2 >= t1)
    }
}
