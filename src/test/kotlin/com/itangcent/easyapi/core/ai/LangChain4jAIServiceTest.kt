package com.itangcent.easyapi.core.ai

import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock

/**
 * Regression tests for [LangChain4jAIService] timeout handling.
 *
 * Verifies design Decision 4: a chat timeout (the underlying
 * [ChatModel.chat] blocking longer than
 * [AiRuntimeConfig.requestTimeoutSec]) is surfaced as a
 * [ChatTimeoutException] — a normal transient exception the retry policy
 * classifies as retryable — rather than propagating as a raw coroutine
 * [TimeoutCancellationException], which the agent layer must rethrow as
 * cooperative cancellation and therefore cannot retry.
 *
 * Plain JUnit 4 + mockito-kotlin; no IDE fixture needed — the service is
 * constructed directly with a mocked [ChatModel].
 */
class LangChain4jAIServiceTest {

    /**
     * When the underlying [ChatModel.chat] blocks longer than
     * [AiRuntimeConfig.requestTimeoutSec], the service must throw
     * [ChatTimeoutException] (NOT a raw [TimeoutCancellationException]).
     *
     * Pre-fix this test fails: `withTimeout` propagates
     * [TimeoutCancellationException] directly, which the agent layer
     * treats as cooperative cancellation and cannot retry. Post-fix the
     * service converts it to [ChatTimeoutException], which the retry
     * policy classifies as transient.
     *
     * The fake [ChatModel] blocks for 2 s while the request timeout is
     * 1 s, so the test completes in ~1–2 s regardless of whether the
     * coroutine runtime interrupts the IO thread.
     */
    @Test
    fun testChatTimeoutSurfacedAsChatTimeoutException() = runBlocking {
        // A fake ChatModel that blocks well past the 1s request timeout.
        // The block runs on Dispatchers.IO inside the service, so a
        // blocking Thread.sleep is representative of a real stalled
        // provider call. InterruptedException is deliberately NOT caught:
        // when withTimeout fires and interrupts the IO thread, the
        // InterruptedException must propagate so withTimeout converts it
        // to TimeoutCancellationException (which the fix then converts to
        // ChatTimeoutException). Catching it would race with the timeout
        // and let the block return normally.
        val chatModel = mock<ChatModel> {
            on { chat(any<ChatRequest>()) } doAnswer {
                Thread.sleep(2_000) // blocks longer than requestTimeoutSec=1
                null // unreachable in practice — the timeout fires first
            }
        }
        val settings = AiRuntimeConfig(
            provider = AiProvider.OPENAI,
            baseUrl = "https://api.openai.com/v1",
            apiKey = "test-key",
            model = "gpt-4o-mini",
            requestTimeoutSec = 1,
            maxRequests = 8
        )
        val service = LangChain4jAIService(chatModel, settings)

        val request = AiChatRequest(
            messages = listOf(AiMessage.User("ping")),
            tools = emptyList(),
            maxTokens = 1
        )

        val thrown: ChatTimeoutException? = try {
            service.chat(request)
            null
        } catch (e: ChatTimeoutException) {
            e
        } catch (e: TimeoutCancellationException) {
            throw AssertionError(
                "TimeoutCancellationException leaked (bug regressed): the " +
                    "service must convert it to ChatTimeoutException. Got: $e"
            )
        }

        assertNotNull(
            "ChatTimeoutException must be thrown on chat timeout",
            thrown
        )
        assertEquals(
            "timeoutMs must match the configured request timeout",
            1_000L,
            thrown!!.timeoutMs
        )
    }
}
