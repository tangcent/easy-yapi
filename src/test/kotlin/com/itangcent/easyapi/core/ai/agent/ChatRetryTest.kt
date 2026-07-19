package com.itangcent.easyapi.core.ai.agent

import com.itangcent.easyapi.core.ai.AiChatResponse
import com.itangcent.easyapi.core.ai.AiMessage
import com.itangcent.easyapi.core.ai.ChatTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Pure unit tests for [ChatRetry] — transient classification ([isTransient])
 * and retry-loop behavior ([chatWithRetry]).
 *
 * No IDE fixture needed — the retry policy is pure logic + a cancellable
 * suspend loop. Uses plain JUnit 4 with `runBlocking` (the project does not
 * depend on `kotlinx-coroutines-test`, so virtual-time `runTest` is
 * unavailable). Backoff is configured to 0 ms to keep tests fast; jitter
 * (0–249 ms real time) is acceptable.
 */
class ChatRetryTest {

    // ==================================================================
    // Task 16: Transient classifier isTransient
    // ==================================================================

    // --- Transient by type ---

    @Test
    fun testTransientByTypeIOException() {
        val retry = ChatRetry(LoopSafetyConfig())
        assertTrue("IOException must be transient", retry.isTransient(IOException()))
    }

    @Test
    fun testTransientByTypeChatTimeoutException() {
        val retry = ChatRetry(LoopSafetyConfig())
        assertTrue(
            "ChatTimeoutException must be transient",
            retry.isTransient(ChatTimeoutException(1000L, RuntimeException("upstream")))
        )
    }

    // --- Transient by message (neutral carrier: RuntimeException) ---

    @Test
    fun testTransientByMessageSubstrings() {
        val retry = ChatRetry(LoopSafetyConfig())
        val transientMessages = listOf(
            "429 Too Many Requests",
            "rate limit exceeded",
            "ratelimit exceeded",
            "request timeout",
            "operation timed out",
            "502 Bad Gateway",
            "503 Service Unavailable",
            "504 Gateway Timeout",
            "service temporarily unavailable",
            "server unavailable",
            "connection reset by peer",
            "broken pipe"
        )
        for (msg in transientMessages) {
            assertTrue(
                "message '$msg' must classify as transient",
                retry.isTransient(RuntimeException(msg))
            )
        }
    }

    // --- Non-transient by type ---

    @Test
    fun testNonTransientByTypeIllegalArgumentException() {
        val retry = ChatRetry(LoopSafetyConfig())
        assertFalse(
            "IllegalArgumentException must be non-transient",
            retry.isTransient(IllegalArgumentException("bad arg"))
        )
    }

    @Test
    fun testNonTransientByTypeIllegalStateException() {
        val retry = ChatRetry(LoopSafetyConfig())
        assertFalse(
            "IllegalStateException must be non-transient",
            retry.isTransient(IllegalStateException("bad state"))
        )
    }

    // --- Non-transient by message (neutral carrier: RuntimeException) ---

    @Test
    fun testNonTransientByMessageSubstrings() {
        val retry = ChatRetry(LoopSafetyConfig())
        val nonTransientMessages = listOf(
            "401 Unauthorized",
            "403 Forbidden",
            "unauthorized access",
            "forbidden resource",
            "invalid api key",
            "invalid_api_key",
            "model not found: gpt-foo",
            "context length exceeded"
        )
        for (msg in nonTransientMessages) {
            assertFalse(
                "message '$msg' must classify as non-transient",
                retry.isTransient(RuntimeException(msg))
            )
        }
    }

    // --- Default: unknown exception type + unknown message → non-transient ---

    @Test
    fun testDefaultUnknownIsNonTransient() {
        val retry = ChatRetry(LoopSafetyConfig())
        assertFalse(
            "unknown exception with unknown message must default to non-transient",
            retry.isTransient(RuntimeException("something totally unexpected"))
        )
    }

    @Test
    fun testNullMessageDefaultsToNonTransient() {
        val retry = ChatRetry(LoopSafetyConfig())
        // RuntimeException with null message — no type match, no message to check.
        assertFalse(
            "exception with null message must default to non-transient",
            retry.isTransient(RuntimeException())
        )
    }

    // --- Case-insensitive message matching ---

    @Test
    fun testMessageMatchingIsCaseInsensitive() {
        val retry = ChatRetry(LoopSafetyConfig())
        assertTrue(
            "uppercase 'RATE LIMIT' must match 'rate limit'",
            retry.isTransient(RuntimeException("RATE LIMIT exceeded"))
        )
        assertTrue(
            "mixed-case 'Timed Out' must match 'timed out'",
            retry.isTransient(RuntimeException("Request Timed Out"))
        )
        assertFalse(
            "uppercase 'UNAUTHORIZED' must match 'unauthorized'",
            retry.isTransient(RuntimeException("UNAUTHORIZED"))
        )
    }

    // ==================================================================
    // Task 17: chatWithRetry suspend helper
    // ==================================================================

    /** Fast config: zero backoff so tests run instantly (jitter 0–249 ms only). */
    private val fastCfg = LoopSafetyConfig(
        chatMaxRetries = 2,
        chatBackoffBaseMs = 0,
        chatBackoffMaxMs = 0
    )

    @Test
    fun testFirstTrySuccessNoRecovery() = runBlocking {
        val retry = ChatRetry(fastCfg)
        val expected = textResponse("hello")
        var recoveryCalled = false
        var failureCalled = false

        val resp = retry.chatWithRetry(
            block = { expected },
            onFailure = { _, _ -> failureCalled = true },
            onRecovery = { recoveryCalled = true }
        )

        assertSame(expected, resp)
        assertFalse("onRecovery must NOT be invoked on first-try success", recoveryCalled)
        assertFalse("onFailure must NOT be invoked on first-try success", failureCalled)
    }

    @Test
    fun testTransientFailureRetriedThenSucceeds() = runBlocking {
        val retry = ChatRetry(fastCfg)
        val expected = textResponse("recovered")
        val scripted = ScriptedBlock()
            .throwNext(IOException("connection reset"))
            .returnNext(expected)
        var recoveryAttempts: Int? = null
        val failures = mutableListOf<Int>()

        val resp = retry.chatWithRetry(
            block = scripted.block,
            onFailure = { attempt, _ -> failures.add(attempt) },
            onRecovery = { attempts -> recoveryAttempts = attempts }
        )

        assertSame(expected, resp)
        assertEquals("block must be called twice (1 fail + 1 success)", 2, scripted.callCount)
        assertEquals("onFailure called once with attempt=1", listOf(1), failures)
        assertEquals("onRecovery must be invoked with attempt=1", 1, recoveryAttempts)
    }

    @Test
    fun testRetriesExhaustedThrowsChatRetriesExhausted() = runBlocking {
        val cfg = LoopSafetyConfig(chatMaxRetries = 2, chatBackoffBaseMs = 0, chatBackoffMaxMs = 0)
        val retry = ChatRetry(cfg)
        val lastException = IOException("fail-3")
        val scripted = ScriptedBlock()
            .throwNext(IOException("fail-1"))
            .throwNext(IOException("fail-2"))
            .throwNext(lastException)
        val failures = mutableListOf<Int>()
        var recoveryCalled = false

        val thrown = try {
            retry.chatWithRetry(
                block = scripted.block,
                onFailure = { attempt, _ -> failures.add(attempt) },
                onRecovery = { recoveryCalled = true }
            )
            null
        } catch (e: ChatRetriesExhausted) {
            e
        }

        assertNotNull("ChatRetriesExhausted must be thrown", thrown)
        assertEquals(
            "attempts must be 1 + chatMaxRetries = 3",
            1 + cfg.chatMaxRetries,
            thrown!!.attempts
        )
        assertSame("cause must be the last exception", lastException, thrown.cause)
        assertEquals("onFailure called per attempt", listOf(1, 2, 3), failures)
        assertFalse("onRecovery must NOT be invoked on exhaustion", recoveryCalled)
    }

    @Test
    fun testNonTransientFailureThrowsImmediately() = runBlocking {
        val retry = ChatRetry(fastCfg)
        val authError = IllegalArgumentException("401 unauthorized")
        val scripted = ScriptedBlock().throwNext(authError)
        val failures = mutableListOf<Int>()
        var recoveryCalled = false

        val thrown = try {
            retry.chatWithRetry(
                block = scripted.block,
                onFailure = { attempt, _ -> failures.add(attempt) },
                onRecovery = { recoveryCalled = true }
            )
            null
        } catch (e: ChatRetriesExhausted) {
            e
        }

        assertNotNull("ChatRetriesExhausted must be thrown", thrown)
        assertEquals("attempts must be 1 (no retry on non-transient)", 1, thrown!!.attempts)
        assertSame("cause must be the original exception", authError, thrown.cause)
        assertEquals("block called exactly once (no retry)", 1, scripted.callCount)
        assertEquals("onFailure called once with attempt=1", listOf(1), failures)
        assertFalse("onRecovery must NOT be invoked", recoveryCalled)
    }

    @Test
    fun testCancellationExceptionRethrown() = runBlocking {
        val retry = ChatRetry(fastCfg)
        val cancelException = CancellationException("user pressed stop")
        val scripted = ScriptedBlock().throwNext(cancelException)
        var onFailureCalled = false
        var onRecoveryCalled = false
        var caught: Throwable? = null

        try {
            retry.chatWithRetry(
                block = scripted.block,
                onFailure = { _, _ -> onFailureCalled = true },
                onRecovery = { onRecoveryCalled = true }
            )
        } catch (e: CancellationException) {
            caught = e
        }

        assertSame("CancellationException must be rethrown as-is", cancelException, caught)
        assertFalse("onFailure must NOT be called on cancellation", onFailureCalled)
        assertFalse("onRecovery must NOT be called on cancellation", onRecoveryCalled)
        assertEquals("block called exactly once", 1, scripted.callCount)
    }

    @Test
    fun testOnFailureInvokedPerAttemptWithAttemptNumber() = runBlocking {
        val retry = ChatRetry(fastCfg)
        val scripted = ScriptedBlock()
            .throwNext(IOException("fail-1"))
            .throwNext(IOException("fail-2"))
            .throwNext(IOException("fail-3"))
        val failureAttempts = mutableListOf<Int>()
        val failureExceptions = mutableListOf<Exception>()

        try {
            retry.chatWithRetry(
                block = scripted.block,
                onFailure = { attempt, e ->
                    failureAttempts.add(attempt)
                    failureExceptions.add(e)
                },
                onRecovery = { }
            )
        } catch (_: ChatRetriesExhausted) {
            // expected
        }

        assertEquals(
            "onFailure must be called with 1-based attempt numbers",
            listOf(1, 2, 3),
            failureAttempts
        )
        assertEquals(
            "onFailure must receive the matching exception per attempt",
            listOf("fail-1", "fail-2", "fail-3"),
            failureExceptions.map { it.message }
        )
    }

    @Test
    fun testChatTimeoutExceptionRetriedAsTransient() = runBlocking {
        val retry = ChatRetry(fastCfg)
        val expected = textResponse("after-timeout")
        val scripted = ScriptedBlock()
            .throwNext(ChatTimeoutException(30_000L, RuntimeException("upstream")))
            .returnNext(expected)
        var recoveryAttempts: Int? = null

        val resp = retry.chatWithRetry(
            block = scripted.block,
            onFailure = { _, _ -> },
            onRecovery = { attempts -> recoveryAttempts = attempts }
        )

        assertSame(expected, resp)
        assertEquals("block called twice (1 timeout + 1 success)", 2, scripted.callCount)
        assertEquals("onRecovery invoked with attempt=1", 1, recoveryAttempts)
    }

    @Test
    fun testZeroMaxRetriesNoRetry() = runBlocking {
        val cfg = LoopSafetyConfig(chatMaxRetries = 0, chatBackoffBaseMs = 0, chatBackoffMaxMs = 0)
        val retry = ChatRetry(cfg)
        val ioError = IOException("transient but no retries allowed")
        val scripted = ScriptedBlock().throwNext(ioError)
        var recoveryCalled = false

        val thrown = try {
            retry.chatWithRetry(
                block = scripted.block,
                onFailure = { _, _ -> },
                onRecovery = { recoveryCalled = true }
            )
            null
        } catch (e: ChatRetriesExhausted) {
            e
        }

        assertNotNull("ChatRetriesExhausted must be thrown", thrown)
        assertEquals("attempts must be 1 (no retries)", 1, thrown!!.attempts)
        assertSame(ioError, thrown.cause)
        assertEquals("block called exactly once", 1, scripted.callCount)
        assertFalse("onRecovery must NOT be invoked", recoveryCalled)
    }

    @Test
    fun testBackoffRetriesOnEachTransientFailure() = runBlocking {
        // Use a non-zero base so we can confirm the loop still completes
        // quickly enough for a unit test (base=10ms, max=10ms → 10ms+jitter per retry).
        val cfg = LoopSafetyConfig(
            chatMaxRetries = 2,
            chatBackoffBaseMs = 10,
            chatBackoffMaxMs = 10
        )
        val retry = ChatRetry(cfg)
        val expected = textResponse("done")
        val scripted = ScriptedBlock()
            .throwNext(IOException("fail-1"))
            .throwNext(IOException("fail-2"))
            .returnNext(expected)
        val failureAttempts = mutableListOf<Int>()
        var recoveryAttempts: Int? = null

        val resp = retry.chatWithRetry(
            block = scripted.block,
            onFailure = { attempt, _ -> failureAttempts.add(attempt) },
            onRecovery = { attempts -> recoveryAttempts = attempts }
        )

        assertSame(expected, resp)
        assertEquals("three block calls (2 fails + 1 success)", 3, scripted.callCount)
        assertEquals("onFailure called for attempts 1 and 2", listOf(1, 2), failureAttempts)
        assertEquals("onRecovery called with attempt=2", 2, recoveryAttempts)
    }

    // --- Helpers ---

    /** Creates a minimal [AiChatResponse] with plain-text assistant content. */
    private fun textResponse(content: String): AiChatResponse =
        AiChatResponse(AiMessage.Assistant(content, null), "stop")

    /**
     * Scripts a sequence of outcomes for the chat block: exceptions (thrown)
     * and responses (returned), in insertion order.
     */
    private class ScriptedBlock {
        private val outcomes = ArrayDeque<Any>()
        private var _callCount = 0
        val callCount: Int get() = _callCount

        fun throwNext(e: Exception): ScriptedBlock {
            outcomes.addLast(e)
            return this
        }

        fun returnNext(r: AiChatResponse): ScriptedBlock {
            outcomes.addLast(r)
            return this
        }

        val block: suspend () -> AiChatResponse = {
            _callCount++
            check(outcomes.isNotEmpty()) { "ScriptedBlock queue exhausted (call #$_callCount)" }
            when (val item = outcomes.removeFirst()) {
                is Exception -> throw item
                is AiChatResponse -> item
                else -> error("unexpected outcome type: ${item::class}")
            }
        }
    }
}
