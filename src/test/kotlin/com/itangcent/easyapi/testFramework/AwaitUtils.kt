package com.itangcent.easyapi.testFramework

import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Test utilities for waiting on asynchronous conditions that depend on cache
 * readiness or IDE/PSI sync latency.
 *
 * Each helper polls a condition at short intervals and throws a descriptive
 * [AssertionError] when the condition is not met within the timeout, replacing
 * fragile fixed [delay]s and silent poll loops (e.g. loops that exit on timeout
 * without failing the test).
 *
 * The [action] lambdas are inlined into a `suspend` context, so they may
 * themselves call `suspend` functions (such as `ApiIndex.endpoints()`).
 *
 * The function-typed parameter is always placed last so callers can use the
 * trailing-lambda syntax.
 */

/**
 * Waits until [action] returns `true`, polling every [pollInterval].
 *
 * @throws AssertionError if the condition is not satisfied within [timeout].
 */
suspend inline fun waitUntil(
    timeout: Duration = 5.seconds,
    pollInterval: Duration = 50.milliseconds,
    crossinline action: suspend () -> Boolean
) {
    val deadlineMs = System.currentTimeMillis() + timeout.inWholeMilliseconds
    while (true) {
        if (action()) return
        if (System.currentTimeMillis() >= deadlineMs) {
            throw AssertionError("Condition not satisfied within $timeout")
        }
        delay(pollInterval.inWholeMilliseconds)
    }
}

/**
 * Waits until [predicate] returns `true` for the value produced by [action].
 *
 * @return the value produced by [action] that satisfied [predicate].
 * @throws AssertionError if the condition is not met within [timeout].
 */
suspend inline fun <T> waitUntil(
    timeout: Duration = 5.seconds,
    pollInterval: Duration = 50.milliseconds,
    crossinline action: suspend () -> T,
    crossinline predicate: (T) -> Boolean
): T {
    val deadlineMs = System.currentTimeMillis() + timeout.inWholeMilliseconds
    while (true) {
        val value = action()
        if (predicate(value)) return value
        if (System.currentTimeMillis() >= deadlineMs) {
            throw AssertionError("Condition not satisfied within $timeout (last value: $value)")
        }
        delay(pollInterval.inWholeMilliseconds)
    }
}

/**
 * Waits until [action] returns a non-null value.
 *
 * @return the first non-null value produced by [action].
 * @throws AssertionError if no non-null value is produced within [timeout].
 */
suspend inline fun <T> waitUntilNotNull(
    timeout: Duration = 5.seconds,
    pollInterval: Duration = 50.milliseconds,
    crossinline action: suspend () -> T?
): T {
    val deadlineMs = System.currentTimeMillis() + timeout.inWholeMilliseconds
    while (true) {
        val value = action()
        if (value != null) return value
        if (System.currentTimeMillis() >= deadlineMs) {
            throw AssertionError("Expected non-null value within $timeout")
        }
        delay(pollInterval.inWholeMilliseconds)
    }
}

/**
 * Waits until [action] returns a non-empty collection.
 *
 * @return the first non-empty collection produced by [action].
 * @throws AssertionError if no non-empty collection is produced within [timeout].
 */
suspend inline fun <T : Collection<*>> waitUntilNotEmpty(
    timeout: Duration = 5.seconds,
    pollInterval: Duration = 50.milliseconds,
    crossinline action: suspend () -> T?
): T {
    val deadlineMs = System.currentTimeMillis() + timeout.inWholeMilliseconds
    var lastSize: Int = 0
    while (true) {
        val value = action()
        if (value != null && value.isNotEmpty()) return value
        lastSize = value?.size ?: 0
        if (System.currentTimeMillis() >= deadlineMs) {
            throw AssertionError("Expected non-empty collection within $timeout (last size: $lastSize)")
        }
        delay(pollInterval.inWholeMilliseconds)
    }
}
