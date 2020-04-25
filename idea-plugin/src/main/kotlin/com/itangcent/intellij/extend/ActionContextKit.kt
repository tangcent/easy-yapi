package com.itangcent.intellij.extend

import com.itangcent.common.concurrent.ValueHolder
import com.itangcent.intellij.context.ActionContext
import java.util.concurrent.TimeUnit

fun ActionContext?.tryRunAsync(action: () -> Unit) {
    if (this == null) {
        action()
    } else {
        this.runAsync(action)
    }
}


fun <T> ActionContext.callWithTimeout(timeout: Long, action: () -> T): T? {
    val resultHolder = ValueHolder<T>()

    val runAsync = this.runAsync { resultHolder.compute { action() } }
    try {
        runAsync!!.get(timeout, TimeUnit.MINUTES)
    } catch (e: Exception) {
        runAsync!!.cancel(true)
        resultHolder.failed(e)
    }
    return resultHolder.value()
}