package com.itangcent.easyapi.ai.agent

import com.itangcent.easyapi.ai.AiChatRequest
import com.itangcent.easyapi.ai.AiChatResponse
import com.itangcent.easyapi.ai.AiMessage
import com.itangcent.easyapi.ai.AIService

/**
 * Scripted test double for [AIService] — test helper.
 *
 * Tests enqueue responses via [enqueue]. Each call to [chat] pops the head
 * of the queue. The fake also records every request it received so tests
 * can assert the agent sent the expected messages/tools.
 *
 * If the queue is empty when [chat] is called, the fake throws — a script
 * underflow is almost always a test bug, not a meaningful runtime scenario.
 */
class FakeAIService : AIService {

    private val queue: ArrayDeque<AiChatResponse> = ArrayDeque()
    private val requests: MutableList<AiChatRequest> = mutableListOf()

    /** Enqueue a response that the next [chat] call will return. */
    fun enqueue(response: AiChatResponse): FakeAIService {
        queue.addLast(response)
        return this
    }

    /** Convenience: enqueue a plain text assistant message (no tool calls). */
    fun enqueueText(text: String): FakeAIService =
        enqueue(AiChatResponse(AiMessage.Assistant(text, null), "stop"))

    /**
     * Convenience: enqueue an assistant message that issues [toolCalls].
     * [content] is the visible assistant text (may be empty).
     */
    fun enqueueToolCalls(
        vararg toolCalls: com.itangcent.easyapi.ai.AiToolCall,
        content: String? = null
    ): FakeAIService =
        enqueue(AiChatResponse(
            AiMessage.Assistant(content, toolCalls.toList()),
            "tool_calls"
        ))

    /** Snapshot of every request the agent sent. */
    fun requests(): List<AiChatRequest> = requests.toList()

    override suspend fun chat(request: AiChatRequest): AiChatResponse {
        // Snapshot the message list — the caller mutates the same list across
        // turns, so without a copy later assertions see post-hoc additions.
        requests.add(request.copy(messages = request.messages.toList()))
        if (queue.isEmpty()) {
            error("FakeAIService queue is empty — script underflow. " +
                "Requests received so far: ${requests.size}.")
        }
        return queue.removeFirst()
    }

    override suspend fun testConnection(): Result<String> =
        Result.success("fake-connection-ok")
}
