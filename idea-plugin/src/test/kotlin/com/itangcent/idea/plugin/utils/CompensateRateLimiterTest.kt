package com.itangcent.idea.plugin.utils

import com.itangcent.intellij.context.ActionContext
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test case of [CompensateRateLimiter]
 */
internal class CompensateRateLimiterTest {

    @Test
    fun tryAcquire() {
        run {
            val compensateRateLimiter = CompensateRateLimiter.create(2)
            val last = AtomicLong(0L)
            val times = AtomicInteger(0)
            val start = System.currentTimeMillis()
            for (i in 0..2) {
                compensateRateLimiter.tryAcquire {
                    val now = System.currentTimeMillis()
                    do {
                        val l = last.get()
                        if (l >= now || last.compareAndSet(l, now)) {
                            break
                        }
                    } while (true)
                    times.incrementAndGet()
                }
            }
            assertTrue { last.get() >= start }
            assertEquals(1, times.get())
            val end = System.currentTimeMillis()
            Thread.sleep(700)
            assertEquals(2, times.get())
            assertTrue { last.get() >= end }
        }
        run {
            val compensateRateLimiter = CompensateRateLimiter.create(2)
            val last = AtomicLong(0L)
            val times = AtomicInteger(0)
            val start = System.currentTimeMillis()
            val until = start + TimeUnit.SECONDS.toMillis(4)
            val lastAcquire = AtomicLong(0L)
            while (until > System.currentTimeMillis()) {
                lastAcquire.set(System.currentTimeMillis())
                compensateRateLimiter.tryAcquire {
                    val now = System.currentTimeMillis()
                    println(now)
                    do {
                        val l = last.get()
                        if (l >= now || last.compareAndSet(l, now)) {
                            break
                        }
                    } while (true)
                    times.incrementAndGet()
                }
                Thread.sleep(100)
            }
            assertTrue { last.get() >= start }
            times.get().let {
                assertTrue { it >= 8 }
                assertTrue { it <= 10 }
            }
            Thread.sleep(700)
            assertTrue { last.get() >= lastAcquire.get() }
        }
    }

    @Test
    fun tryAcquireWithContext() {
        val actionContext = ActionContext.ActionContextBuilder()
            .build()
        actionContext.runAsync {
            tryAcquire()
        }
        actionContext.waitComplete()
    }
}