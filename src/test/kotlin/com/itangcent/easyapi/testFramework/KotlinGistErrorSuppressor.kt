package com.itangcent.easyapi.testFramework

import com.intellij.testFramework.LoggedErrorProcessor
import java.lang.reflect.Field
import java.util.EnumSet

/**
 * Suppresses the IntelliJ test framework's tendency to turn the Kotlin plugin's
 * `Can't read GistImpl[kotlin-library-kind]` diagnostic into a hard test failure.
 *
 * In light PSI test fixtures the Kotlin plugin's [ResolveScopeEnlarger] chain
 * tries to read a `VirtualFileGist` that has no backing storage, and reports it
 * via `Logger.error(...)`. The default [LoggedErrorProcessor] returns
 * [LoggedErrorProcessor.Action.ALL][EnumSet.of] which includes `RETHROW`, so
 * `TestLoggerFactory` wraps the error in a `TestLoggerAssertionError` and the
 * test aborts — even though the PSI traversal still produces correct results.
 *
 * This processor demotes that single known noise error to `LOG` only, while
 * leaving every other error on its default (rethrow) behaviour. It is installed
 * globally (by reflecting on `LoggedErrorProcessor.ourInstance`) for the
 * duration of a test class via [install] / [uninstall].
 *
 * The suppressed message fragment matches the upstream gist name
 * (`kotlin-library-kind`) rather than the whole message, so it stays robust
 * against cosmetic changes to the surrounding log text.
 */
class KotlinGistErrorSuppressor private constructor(
    private val delegate: LoggedErrorProcessor
) : LoggedErrorProcessor() {

    override fun processError(
        loggerName: String,
        message: String,
        details: Array<out String>,
        t: Throwable?
    ): Set<Action> {
        if (isKotlinLibraryKindGistError(message, t)) {
            // Demote to LOG only — do not rethrow, do not dump to stderr.
            return EnumSet.of(Action.LOG)
        }
        return delegate.processError(loggerName, message, details, t)
    }

    private fun isKotlinLibraryKindGistError(message: String, t: Throwable?): Boolean {
        if (message.contains("kotlin-library-kind")) return true
        val root = t?.message ?: return false
        return root.contains("kotlin-library-kind")
    }

    companion object {
        private val ourInstanceField: Field by lazy {
            LoggedErrorProcessor::class.java.getDeclaredField("ourInstance").apply {
                isAccessible = true
            }
        }

        /**
         * Installs this suppressor as the global [LoggedErrorProcessor], wrapping
         * the current instance. Safe to call multiple times — subsequent calls are
         * no-ops once already installed.
         *
         * @return the previous processor so it can be restored via [uninstall].
         */
        fun install(): LoggedErrorProcessor {
            val field = ourInstanceField
            val current = field.get(null) as? LoggedErrorProcessor
                ?: LoggedErrorProcessor()
            if (current is KotlinGistErrorSuppressor) return current
            val suppressor = KotlinGistErrorSuppressor(current)
            field.set(null, suppressor)
            return current
        }

        /**
         * Restores the processor previously returned by [install].
         */
        fun uninstall(previous: LoggedErrorProcessor) {
            ourInstanceField.set(null, previous)
        }
    }
}
