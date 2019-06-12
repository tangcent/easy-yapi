package com.itangcent.idea.utils

import com.itangcent.intellij.logger.Logger
import org.apache.commons.lang3.exception.ExceptionUtils

fun Logger.traceError(e: Throwable) {
    this.trace(ExceptionUtils.getStackTrace(e))
}