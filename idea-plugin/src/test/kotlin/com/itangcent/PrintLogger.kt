package com.itangcent

import com.google.inject.Singleton
import com.itangcent.intellij.logger.AbstractLogger

@Singleton
class PrintLogger : AbstractLogger() {

    override fun processLog(logData: String?) {
        println(logData)
    }
}