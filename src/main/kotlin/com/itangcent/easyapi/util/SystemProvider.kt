package com.itangcent.easyapi.util

/**
 * Provider for system-level operations.
 *
 * Provides a testable abstraction over system functions.
 * Can be mocked in tests to control time-based behavior.
 *
 * ## Usage
 * ```kotlin
 * // Get current time
 * val time = systemProvider.currentTimeMillis()
 *
 * // In tests, use a mock
 * class MockSystemProvider : SystemProvider() {
 *     override fun currentTimeMillis() = 1234567890L
 * }
 * ```
 */
open class SystemProvider {
    /**
     * Returns the current time in milliseconds.
     *
     * @return The current time in milliseconds since epoch
     */
    open fun currentTimeMillis(): Long = System.currentTimeMillis()
}
