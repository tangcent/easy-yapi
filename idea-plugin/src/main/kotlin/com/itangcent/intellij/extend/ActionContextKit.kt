package com.itangcent.intellij.extend

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.itangcent.common.concurrent.ValueHolder
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.TimeSpanUtils
import com.itangcent.common.utils.cast
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.context.ThreadFlag
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.util.ActionUtils
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

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
        boundary.waitComplete()
    } catch (e: Throwable) {
        boundary.waitComplete()
        throw e
    }
}

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

fun ActionContext.runWithContext(action: () -> Unit) {
    val context = ActionContext.getContext()
    if (context == null) {
        this.runAsync(action)
    } else {
        action()
    }
}

fun ActionContext.runInNormalThread(action: () -> Unit) {
    val flag = ActionContext.getFlag()
    if (flag == ThreadFlag.ASYNC.value) {
        action()
    } else {
        this.runAsync(action)
    }
}

fun ActionContext.findCurrentMethod(): PsiMethod? {
    return this.cacheOrCompute("_currentMethod") {
        ActionUtils.findCurrentMethod()
    }
}
