package com.itangcent.easyapi.core.ai

/**
 * Provider-neutral DTOs used by the AI agent and its tools.
 * These decouple the plugin from any specific LLM SDK (LangChain4j, etc.).
 */

/**
 * A single chat request to the LLM.
 *
 * @param messages Conversation history (system, user, assistant, tool-result).
 * @param tools Tool specifications the model may invoke.
 * @param maxTokens Optional cap on response tokens.
 */
data class AiChatRequest(
    val messages: List<AiMessage>,
    val tools: List<AiToolSpec>,
    val maxTokens: Int? = null
)

/**
 * A message in the conversation, sealed by role.
 */
sealed class AiMessage {
    /** System prompt establishing the agent's persona / rules. */
    data class System(val content: String) : AiMessage()

    /** User input. */
    data class User(val content: String) : AiMessage()

    /** Model output — may include text content and/or tool calls. */
    data class Assistant(
        val content: String?,
        val toolCalls: List<AiToolCall>?
    ) : AiMessage()

    /** Result of executing a tool, fed back to the model. */
    data class ToolResult(
        val toolCallId: String,
        val name: String,
        val content: String
    ) : AiMessage()
}

/**
 * Specification of a tool the model can call.
 *
 * @param name Tool name (unique within a request).
 * @param description Human-readable description for the model.
 * @param parametersJsonSchema JSON Schema string describing the parameters.
 */
data class AiToolSpec(
    val name: String,
    val description: String,
    val parametersJsonSchema: String
)

/**
 * A tool call issued by the model.
 *
 * @param id Unique call id (used to correlate with [AiMessage.ToolResult]).
 * @param name Tool name to invoke.
 * @param arguments JSON string of arguments.
 */
data class AiToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

/**
 * Response from the LLM.
 *
 * @param message The assistant message (content and/or tool calls).
 * @param finishReason Why the model stopped ("stop", "tool_calls", "length", etc.).
 */
data class AiChatResponse(
    val message: AiMessage,
    val finishReason: String?
)
