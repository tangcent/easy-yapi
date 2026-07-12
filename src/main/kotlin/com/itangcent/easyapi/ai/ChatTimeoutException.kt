package com.itangcent.easyapi.ai

/**
 * Raised when a chat call exceeds its request timeout.
 *
 * Produced by converting the underlying coroutine
 * `TimeoutCancellationException` so that the timeout becomes a normal
 * transient exception the retry policy can handle, while cooperative
 * cancellation (`Job.cancel`) still propagates untouched.
 *
 * @param timeoutMs The configured timeout in milliseconds that was exceeded.
 * @param cause The original timeout exception.
 */
class ChatTimeoutException(
    val timeoutMs: Long,
    cause: Throwable
) : RuntimeException("chat timed out after ${timeoutMs}ms", cause)
