package com.itangcent.idea.plugin.utils

import com.google.common.util.concurrent.RateLimiter
import com.itangcent.intellij.context.ActionContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

@Suppress("UnstableApiUsage")
class CompensateRateLimiter private constructor(
    permitsPerSecond: Double,
) {

    private val rateLimiter = RateLimiter.create(permitsPerSecond)

    @Volatile
    private var compensateStatus = 0

    private val delayCompensate: Long = ceil(1000.0 / permitsPerSecond).toLong()

    private var lastAcquire = AtomicLong(0L)

    fun tryAcquire(action: () -> Unit) {
        if (rateLimiter.tryAcquire()) {
            execute(action)
        } else {
            lastAcquire.update()
            tryCompensate(action)
        }
    }

    private fun execute(action: () -> Unit) {
        action()
    }

    private fun AtomicLong.update() {
        val now = System.currentTimeMillis()
        while (true) {
            val curr = this.get()
            if (curr >= now || this.compareAndSet(curr, now)) {
                break
            }
        }
    }

    private fun tryCompensate(action: () -> Unit) {
        synchronized(this) {
            if (compensateStatus == 1) {
                return
            }
            compensateStatus = 1
            compensate(action)
        }
    }

    private fun compensate(action: () -> Unit) {
        runAsync {
            try {
                while (true) {
                    rateLimiter.acquire()
                    execute(action)
                    var completed = false
                    synchronized(this) {
                        if (lastAcquire.get() + delayCompensate <= System.currentTimeMillis()) {
                            completed = true
                        }
                    }
                    if (completed) {
                        break
                    }
                }
            } finally {
                compensateStatus = 0
            }
        }
    }

    private fun runAsync(action: () -> Unit) {
        val context = ActionContext.getContext()
        if (context != null) {
            context.runAsync(action)
        } else {
            Thread(action).start()
        }
    }

    companion object {
        fun create(permitsPerSecond: Number): CompensateRateLimiter {
            return CompensateRateLimiter(permitsPerSecond.toDouble())
        }
    }
}