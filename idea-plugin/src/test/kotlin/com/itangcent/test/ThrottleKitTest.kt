package com.itangcent.test

import com.itangcent.intellij.extend.acquireGreedy
import com.itangcent.intellij.extend.rx.ThrottleHelper
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertTimeout
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.time.Duration


/**
 * Test case for [ThrottleKit]
 */
@RunWith(JUnit4::class)
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