package com.itangcent.easyapi.core.ai.agent

import com.itangcent.easyapi.core.ai.AiChatResponse
import com.itangcent.easyapi.core.ai.ChatTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException
import kotlin.random.Random

/**
 * Thrown when chat retries are exhausted (terminal).
 *
 * Wraps the last failure cause and carries the final attempt count so the
 * caller can surface both in a terminal failure signal. The original cause
 * is preserved as [cause].
 *
 * @param attempts Total number of attempts made (1 + number of retries).
 * @param cause The last exception that triggered exhaustion.
 */
class ChatRetriesExhausted(
    val attempts: Int,
    cause: Throwable
) : RuntimeException("chat failed after $attempts attempt(s)", cause)

/**
 * Bounded retry with exponential backoff for transient chat failures.
 *
 * Wraps a chat [block] so that transient failures (network errors, timeouts,
 * 429/5xx responses) are retried with capped exponential backoff plus jitter,
 * while deterministic failures (auth errors, illegal arguments) fail fast.
 * Cooperative cancellation ([CancellationException]) is always rethrown —
 * never swallowed or delayed — so the Stop button remains responsive
 * mid-backoff.
 *
 * Classification and the retry loop are separated deliberately: [isTransient]
 * is a pure function over an exception (trivially unit-testable), while
 * [chatWithRetry] is the suspending loop that drives backoff and callbacks.
 *
 * @param cfg Loop-safety tuning (retry count + backoff parameters).
 */
class ChatRetry(private val cfg: LoopSafetyConfig) {

    /**
     * Execute [block] with bounded retry on transient failures.
     *
     * On success after one or more retries, [onRecovery] is invoked with the
     * total attempt count so the caller can log/emit a recovery signal.
     * [onFailure] is invoked per failed attempt with the 1-based attempt
     * number and the exception, so the caller can log the failure and emit a
     * non-terminal retry-progress event.
     *
     * Terminal failure (non-transient fail-fast OR retries exhausted) throws
     * [ChatRetriesExhausted] carrying the final attempt count and last cause.
     * [CancellationException] is rethrown immediately — it never reaches
     * [onFailure] or [onRecovery].
     *
     * Backoff between retries is `min(chatBackoffMaxMs,
     * chatBackoffBaseMs * 2^(attempt-1))` plus 0–249 ms jitter, realised via
     * cancellable [delay] (never `Thread.sleep`).
     *
     * @param block The chat call to execute (may be retried on transient failure).
     * @param onFailure Per-attempt failure hook (1-based attempt number + exception).
     * @param onRecovery Recovery hook invoked on success after retry (attempt > 0).
     * @return The successful [AiChatResponse] from [block].
     */
    suspend fun chatWithRetry(
        block: suspend () -> AiChatResponse,
        onFailure: (attempt: Int, e: Exception) -> Unit,
        onRecovery: (attempts: Int) -> Unit
    ): AiChatResponse {
        var attempt = 0
        while (true) {
            try {
                val resp = block()
                if (attempt > 0) onRecovery(attempt)
                return resp
            } catch (e: CancellationException) {
                // Cooperative cancellation must never be swallowed or delayed.
                throw e
            } catch (e: Exception) {
                attempt++
                onFailure(attempt, e)
                if (!isTransient(e) || attempt > cfg.chatMaxRetries) {
                    throw ChatRetriesExhausted(attempt, e)
                }
                val backoff = minOf(
                    cfg.chatBackoffMaxMs,
                    cfg.chatBackoffBaseMs * (1L shl (attempt - 1))
                )
                delay(backoff + Random.nextLong(0, 250))
            }
        }
    }

    /**
     * Classify whether [e] may succeed on retry.
     *
     * Returns `true` for transient failures — [IOException] (including
     * `SocketTimeoutException`), [ChatTimeoutException], or any exception
     * whose (lowercased) message contains a transient substring
     * (`"429"`, `"rate limit"`, `"ratelimit"`, `"timeout"`, `"timed out"`,
     * `"502"`, `"503"`, `"504"`, `"temporarily"`, `"unavailable"`,
     * `"connection reset"`, `"broken pipe"`).
     *
     * Returns `false` for deterministic failures —
     * [IllegalArgumentException], [IllegalStateException], or any exception
     * whose message contains a non-transient substring (`"401"`, `"403"`,
     * `"unauthorized"`, `"forbidden"`, `"invalid api key"`,
     * `"invalid_api_key"`, `"model not found"`, `"context length"`) — and
     * for unknown exceptions (conservative fail-fast).
     *
     * [CancellationException] is never classified here: it is rethrown by
     * [chatWithRetry] before this method is called.
     *
     * @param e The exception to classify.
     * @return `true` if the failure is transient and worth retrying.
     */
    fun isTransient(e: Exception): Boolean {
        // Deny-list by type — fail fast on deterministic errors.
        when (e) {
            is IllegalArgumentException, is IllegalStateException -> return false
        }
        // Allow-list by type.
        when (e) {
            is IOException -> return true
            is ChatTimeoutException -> return true
        }
        // Message-based classification (case-insensitive).
        val msg = e.message?.lowercase() ?: return false
        if (NON_TRANSIENT_SUBSTRINGS.any { msg.contains(it) }) return false
        if (TRANSIENT_SUBSTRINGS.any { msg.contains(it) }) return true
        // Default: non-transient (conservative — don't burn retries on unknowns).
        return false
    }

    private companion object {
        private val TRANSIENT_SUBSTRINGS = listOf(
            "429", "rate limit", "ratelimit", "timeout", "timed out",
            "502", "503", "504", "temporarily", "unavailable",
            "connection reset", "broken pipe"
        )

        private val NON_TRANSIENT_SUBSTRINGS = listOf(
            "401", "403", "unauthorized", "forbidden",
            "invalid api key", "invalid_api_key",
            "model not found", "context length"
        )
    }
}
