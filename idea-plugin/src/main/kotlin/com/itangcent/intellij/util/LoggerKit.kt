package com.itangcent.intellij.util

import com.itangcent.common.exception.ProcessCanceledException
import com.itangcent.intellij.logger.Logger
import org.apache.commons.lang3.exception.ExceptionUtils

fun Logger.traceError(e: Throwable) {
    if (e !is ProcessCanceledException) {
        this.trace(ExceptionUtils.getStackTrace(e))
    }
}