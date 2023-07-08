package com.itangcent.intellij.extend

import com.intellij.psi.PsiMethod
import com.itangcent.common.concurrent.ValueHolder
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.TimeSpanUtils
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.context.ThreadFlag
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.util.ActionUtils
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs the specified action asynchronously with the given ActionContext instance.
 * If the instance is null, the action is run synchronously.
 */
fun ActionContext?.tryRunAsync(action: () -> Unit) {
    if (this == null) {
        action()
    } else {
        this.runAsync(action)
    }
}

/**
 * Calls the specified action with a timeout and returns the result.
 * If the action takes longer than the specified timeout, a TimeoutException is thrown.
 */
fun <T> ActionContext.callWithTimeout(timeout: Long, action: () -> T): T? {
    val resultHolder = ValueHolder<T>()

    val runAsync = this.runAsync {
        resultHolder.compute {
            try {
                action()
            } catch (e: InterruptedException) {
                throw TimeoutException("callWithTimeout " + TimeSpanUtils.pretty(timeout, TimeUnit.MILLISECONDS))
            }
        }
    }
    try {
        runAsync!!.get(timeout, TimeUnit.MILLISECONDS)
    } catch (e: TimeoutException) {
        runAsync!!.cancel(true)
    } catch (e: Exception) {
        //ignore
    }

    return resultHolder.value()
}

/**
 * Creates a boundary and executes the specified action within the boundary.
 * The boundary is waited for completion before returning.
 */
fun ActionContext.withBoundary(action: () -> Unit) {
    val boundary = this.createBoundary()
    try {
        action()
        boundary.waitComplete()
    } catch (e: Throwable) {
        boundary.waitComplete()
        throw e
    }
}

/**
 * Executes the specified action only if the current ActionContext is not reentrant for the specified flag.
 * This is useful for preventing recursive calls to the same method.
 */
private val reentrantIdx = AtomicInteger()

fun ActionContext.notReentrant(flag: String, action: () -> Unit) {
    val idx = reentrantIdx.getAndIncrement()
    if (this.cacheOrCompute("reentrantFlag-${flag}") { idx } == idx) {
        try {
            action()
        } finally {
            this.deleteCache<Int>("reentrantFlag-${flag}")
        }
    }
}

/**
 * Creates a boundary and executes the specified action within the boundary.
 * The boundary is waited for completion before returning the result.
 */
fun <T> ActionContext.callWithBoundary(action: () -> T): T? {
    val boundary = this.createBoundary()
    var ret: T? = null
    try {
        ret = action()
    } catch (e: Exception) {
        ActionContext.instance(Logger::class).traceError(e)
    }
    boundary.waitComplete()
    return ret
}

/**
 * Creates a boundary and executes the specified action within the boundary.
 * The boundary is waited for completion with a timeout.
 */
fun ActionContext.withBoundary(timeOut: Long, action: () -> Unit) {
    val boundary = this.createBoundary()
    try {
        action()
    } catch (e: Throwable) {
        ActionContext.instance(Logger::class).traceError(e)
    }
    if (!boundary.waitComplete(timeOut)) {
        boundary.close()
    }
}

/**
 * Runs the specified action with the current ActionContext instance.
 * If no instance is available, the action is run asynchronously.
 */
fun ActionContext.runWithContext(action: () -> Unit) {
    val context = ActionContext.getContext()
    if (context == null) {
        this.runAsync(action)
    } else {
        action()
    }
}

/**
 * Runs the specified action in a normal thread if the current ActionContext is not already in an asynchronous thread.
 */
fun ActionContext.runInNormalThread(action: () -> Unit) {
    val flag = ActionContext.getFlag()
    if (flag == ThreadFlag.ASYNC.value) {
        action()
    } else {
        this.runAsync(action)
    }
}

/**
 * Finds the current method which is selected by the current Action.
 */
fun ActionContext.findCurrentMethod(): PsiMethod? {
    return this.cacheOrCompute("_currentMethod") {
        ActionUtils.findCurrentMethod()
    }
}