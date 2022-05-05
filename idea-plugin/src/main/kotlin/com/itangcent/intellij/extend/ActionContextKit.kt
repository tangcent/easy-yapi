package com.itangcent.intellij.extend

import com.itangcent.common.concurrent.ValueHolder
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.TimeSpanUtils
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

fun ActionContext?.tryRunAsync(action: () -> Unit) {
    if (this == null) {
        action()
    } else {
        this.runAsync(action)
    }
}


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

fun ActionContext.withBoundary(action: () -> Unit) {
    val boundary = this.createBoundary()
    try {
        action()
    } catch (e: Exception) {
        ActionContext.instance(Logger::class).traceError(e)
    }
    boundary.waitComplete()
}