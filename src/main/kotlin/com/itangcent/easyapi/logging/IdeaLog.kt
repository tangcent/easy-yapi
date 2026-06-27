package com.itangcent.easyapi.logging

import com.intellij.openapi.diagnostic.Logger

/**
 * Interface providing access to IntelliJ's logging facility.
 *
 * Classes implementing this interface gain access to a [Logger] instance
 * via the [LOG] property. The logger is automatically initialized with
 * the implementing class's name.
 *
 * ## `LOG.error` is prohibited
 *
 * `Logger.error` triggers an intrusive error-report popup in the IDE and throws
 * `TestLoggerAssertionError` in tests. Production code MUST NOT call `LOG.error`.
 * Use `LOG.warn` as the error-level fallback. A CI gate test
 * (`AntiPatternGateTest.noLogErrorInProductionCode`) enforces this prohibition.
 *
 * ## Usage
 * ```kotlin
 * class MyService : IdeaLog {
 *     fun doSomething() {
 *         LOG.info("Doing something")
 *         LOG.debug("Debug details", exception)
 *     }
 * }
 * ```
 */
interface IdeaLog {
    /**
     * The logger instance for this class.
     */
    val LOG: Logger get() = Logger.getInstance(this::class.java)
}
