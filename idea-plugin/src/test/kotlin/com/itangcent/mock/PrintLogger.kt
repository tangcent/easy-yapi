package com.itangcent.mock

import com.google.inject.Singleton
import com.itangcent.intellij.logger.AbstractLogger

@Singleton
class PrintLogger : AbstractLogger() {

    override fun processLog(logData: String?) {
        println(logData)
    }
}