package com.itangcent.debug

import com.itangcent.intellij.logger.AbstractLogger
import com.itangcent.intellij.logger.Logger

/**
 *
 * This interface represents [Logger] which collect all appended logs to a buffer.
 */
class LoggerCollector : AbstractLogger() {

    override fun processLog(logData: String?) {
        buffer.append(logData)
            .appendLine()
    }

    companion object {

        /**
         * Cache logs which be added by [LoggerCollector.log].
         */
        private val buffer: StringBuilder = StringBuilder()

        /**
         * Get log in [buffer].
         * The [buffer] will be clear after return.
         *
         * @return all log in [buffer]
         */
        fun getLog(): String {
            val str = buffer.toString()
            buffer.clear()
            return str
        }
    }
}