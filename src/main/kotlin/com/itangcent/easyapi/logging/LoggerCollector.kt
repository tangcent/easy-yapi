package com.itangcent.easyapi.logging

import java.util.concurrent.CopyOnWriteArrayList

class LoggerCollector : IdeaConsole {
    private val messages = CopyOnWriteArrayList<LogMessage>()

    fun getMessages(): List<LogMessage> = messages.toList()

    override fun trace(msg: String) {
        messages.add(LogMessage(LogLevel.TRACE, msg))
    }

    override fun debug(msg: String) {
        messages.add(LogMessage(LogLevel.DEBUG, msg))
    }

    override fun info(msg: String) {
        messages.add(LogMessage(LogLevel.INFO, msg))
    }

    override fun warn(msg: String, t: Throwable?) {
        messages.add(LogMessage(LogLevel.WARN, throwableSuffix(msg, t)))
    }

    override fun error(msg: String, t: Throwable?) {
        messages.add(LogMessage(LogLevel.ERROR, throwableSuffix(msg, t)))
    }

    private fun throwableSuffix(msg: String, t: Throwable?): String {
        return if (t == null) msg else "$msg (${t::class.java.name}: ${t.message})"
    }
}
