package com.itangcent.easyapi.logging

import com.itangcent.easyapi.settings.Settings

/**
 * A settings-aware wrapper around [IdeaConsole] that filters log output based on configured log level.
 *
 * Delegates actual logging to the wrapped [IdeaConsole] instance, but only outputs messages
 * that meet or exceed the configured log level threshold from [Settings].
 *
 * ## Log Level Filtering
 * - TRACE (0): Most verbose, includes all messages
 * - DEBUG (1): Debug and above
 * - INFO (2): Informational and above
 * - WARN (3): Warnings and errors only
 * - ERROR (4): Errors only
 * - 100+: Disabled (no output)
 *
 * @param delegate The underlying [IdeaConsole] to delegate to
 * @param settings The settings containing the log level configuration
 * @see IdeaConsole for the console interface
 * @see DefaultIdeaConsole for the default implementation
 */
class ConfigurableIdeaConsole(
    private val delegate: IdeaConsole,
    private val settings: Settings
) : IdeaConsole {
    override fun trace(msg: String) {
        if (enabled(LogLevel.TRACE)) delegate.trace(msg)
    }

    override fun debug(msg: String) {
        if (enabled(LogLevel.DEBUG)) delegate.debug(msg)
    }

    override fun info(msg: String) {
        if (enabled(LogLevel.INFO)) delegate.info(msg)
    }

    override fun warn(msg: String, t: Throwable?) {
        if (enabled(LogLevel.WARN)) delegate.warn(msg, t)
    }

    override fun error(msg: String, t: Throwable?) {
        if (enabled(LogLevel.ERROR)) delegate.error(msg, t)
    }

    private fun enabled(level: LogLevel): Boolean {
        val configured = settings.logLevel.coerceIn(0, 100)
        if (configured >= 100) return false
        return level.threshold >= configured
    }
}
