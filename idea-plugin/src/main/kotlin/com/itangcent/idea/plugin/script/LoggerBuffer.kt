package com.itangcent.idea.plugin.script

import com.itangcent.intellij.logger.Logger
import java.util.*

class LoggerBuffer : Logger {

    private var buf: LinkedList<(Logger) -> Unit>? = null

    override fun log(level: Logger.Level, msg: String) {
        if (buf == null) {
            buf = LinkedList()
        }
        buf?.add { it.log(level, msg) }
    }

    fun drainTo(logger: Logger) {
        buf?.forEach { it(logger) }
    }
}