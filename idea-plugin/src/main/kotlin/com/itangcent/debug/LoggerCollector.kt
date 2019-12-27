package com.itangcent.debug

import com.itangcent.intellij.logger.Logger

class LoggerCollector : Logger {

    @Synchronized
    override fun log(level: Logger.Level, msg: String) {
        buffer.appendln(msg)
    }

    companion object {

        private val buffer: StringBuilder = StringBuilder()

        fun getLog(): String {
            return buffer.toString()
        }
    }
}