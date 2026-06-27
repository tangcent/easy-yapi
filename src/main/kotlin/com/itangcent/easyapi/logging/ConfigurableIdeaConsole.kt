package com.itangcent.easyapi.logging

import com.itangcent.easyapi.settings.Settings

/**
 * A settings-aware wrapper around [IdeaConsole] that filters log output based on configured log level.
 *
 * Delegates actual logging to the wrapped [IdeaConsole] instance, but only outputs messages
 * that meet or exceed the configured log level threshold from [Settings].
 *
 * ## Log Level Filtering
 *
 * `Settings.logLevel` uses the same 0/10/20/30/40/100 scale as [LogLevel.threshold]:
 * - TRACE (0): Most verbose, includes all messages
 * - DEBUG (10): Debug and above
 * - INFO (20): Informational and above
 * - WARN (30): Warnings and errors only
 * - ERROR (40): Errors only
 * - 100+: Disabled (no output)
 *
 * ## Mirror to idea.log
 *
 * `warn` and `error` are mirrored to `idea.log` via [IdeaLog.LOG] **above** the level
 * filter gate, so a warn/error always reaches the durable log even when the user has turned
 * the console verbosity down. `info`/`debug`/`trace` are not mirrored (they are high-volume
 * and would flood `idea.log`).
 *
 * @param delegate The underlying [IdeaConsole] to delegate to
 * @param settings The settings containing the log level configuration
 * @see IdeaConsole for the console interface
 * @see DefaultIdeaConsole for the default implementation
 */
class ConfigurableIdeaConsole(
    private val delegate: IdeaConsole,
    private val settings: Settings
) : IdeaConsole, IdeaLog {

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
        // Mirror BEFORE the filter gate so warn always reaches idea.log.
        if (t == null) {
            LOG.warn(msg)
        } else {
            LOG.warn(msg, t)
        }
        if (enabled(LogLevel.WARN)) delegate.warn(msg, t)
    }

    override fun error(msg: String, t: Throwable?) {
        // Mirror BEFORE the filter gate so error always reaches idea.log.
        // Use warn level: LOG.error is prohibited (triggers intrusive popup); warn preserves severity without popup.
        if (t == null) {
            LOG.warn(msg)
        } else {
            LOG.warn(msg, t)
        }
        if (enabled(LogLevel.ERROR)) delegate.error(msg, t)
    }

    private fun enabled(level: LogLevel): Boolean {
        val configured = settings.logLevel.coerceIn(0, 100)
        if (configured >= 100) return false
        return level.threshold >= configured
    }
}
