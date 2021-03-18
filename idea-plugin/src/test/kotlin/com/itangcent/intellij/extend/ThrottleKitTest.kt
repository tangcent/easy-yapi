package com.itangcent.intellij.extend

import com.itangcent.intellij.extend.rx.ThrottleHelper
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeout
import java.time.Duration


/**
 * Test case for [ThrottleKit]
 */
class ThrottleKitTest {

    @Test
    fun testAcquireGreedy() {
        val throttleHelper = ThrottleHelper()
        val throttle = throttleHelper.build("test")
        throttle.acquire(1000)
        val now = System.currentTimeMillis()
        assertTimeout(Duration.ofMillis(10)) { throttle.acquire(1000) }
        assertFalse(throttle.acquire(1000))
        throttle.acquireGreedy(1000)
        assertTrue(now + 99 <= System.currentTimeMillis())
        Thread.sleep(1000)
        assertTrue(throttle.acquire(1000))
    }
}