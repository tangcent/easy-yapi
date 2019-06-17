package com.itangcent.intellij.extend

import com.itangcent.common.function.ResultHolder
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
    val resultHolder = ResultHolder<T>()

    val runAsync = this.runAsync { resultHolder.setResultVal(action()) }
    try {
        runAsync!!.get(timeout, TimeUnit.MINUTES)
    } catch (e: Exception) {
        runAsync!!.cancel(true)
        resultHolder.setResultVal(null)
    }
    return resultHolder.getResultVal()
}