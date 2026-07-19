package com.itangcent.easyapi.core.ai

import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.AiMessage as Lc4jAiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.request.json.JsonArraySchema
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema
import dev.langchain4j.model.chat.request.json.JsonNumberSchema
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import dev.langchain4j.model.chat.request.json.JsonSchemaElement
import dev.langchain4j.model.chat.request.json.JsonStringSchema
import dev.langchain4j.model.chat.response.ChatResponse
import com.itangcent.easyapi.core.util.json.GsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.ApiStatus

/**
 * LangChain4j-backed implementation of [AIService].
 *
 * Adapts between the plugin's provider-neutral DTOs ([AiMessage], [AiToolSpec],
 * [AiChatResponse]) and LangChain4j's types ([ChatMessage], [ToolSpecification],
 * [ChatResponse]). The underlying [ChatModel.chat] is blocking, so calls are
 * dispatched to [Dispatchers.IO] with a timeout derived from
 * [AiRuntimeConfig.requestTimeoutSec].
 *
 * @param chatModel The LangChain4j chat model (provider-specific, built by
 * [AIServiceFactory]).
 * @param settings Resolved AI settings (used for timeout).
 */
class LangChain4jAIService(
    private val chatModel: ChatModel,
    private val settings: AiRuntimeConfig
) : AIService {

    override suspend fun chat(request: AiChatRequest): AiChatResponse {
        val lc4jMessages = request.messages.map { it.toLangChain4j() }
        val lc4jTools = request.tools.map { it.toLangChain4j() }

        val builder = ChatRequest.builder()
.messages(lc4jMessages)

        if (lc4jTools.isNotEmpty()) {
            builder.toolSpecifications(lc4jTools)
        }
        request.maxTokens?.let { builder.maxOutputTokens(it) }

        // withTimeout MUST wrap withContext (not the other way around): a
        // blocking Java call inside withTimeout has no suspension point, so
        // withTimeout would silently return the block's value instead of
        // throwing. Wrapping withContext gives a real suspension point
        // (dispatcher switch) so the timeout is enforced.
        val resp = try {
            withTimeout(settings.requestTimeoutSec * 1000L) {
                withContext(Dispatchers.IO) {
                    chatModel.chat(builder.build())
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw ChatTimeoutException(settings.requestTimeoutSec * 1000L, e)
        }
        return resp.toAiChatResponse()
    }

    override suspend fun testConnection(): Result<String> = runCatching {
        val resp = chat(AiChatRequest(
            messages = listOf(AiMessage.User("ping")),
            tools = emptyList(),
            maxTokens = 1
        ))
        (resp.message as? AiMessage.Assistant)?.content ?: "(empty)"
    }

    // --- Private adapter extensions ---

    @ApiStatus.Internal
    private fun AiMessage.toLangChain4j(): ChatMessage = when (this) {
        is AiMessage.System -> SystemMessage.from(content)
        is AiMessage.User -> UserMessage.from(content)
        is AiMessage.Assistant -> {
            val toolReqs = toolCalls?.map { call ->
                ToolExecutionRequest.builder()
.id(call.id)
.name(call.name)
.arguments(call.arguments)
.build()
            }
            if (toolReqs.isNullOrEmpty()) {
                Lc4jAiMessage.from(content ?: "")
            } else {
                Lc4jAiMessage.from(content ?: "", toolReqs)
            }
        }
        is AiMessage.ToolResult -> ToolExecutionResultMessage.from(
            toolCallId, name, content
        )
    }

    @ApiStatus.Internal
    private fun AiToolSpec.toLangChain4j(): ToolSpecification {
        val builder = ToolSpecification.builder()
.name(name)
.description(description)
        // Attach the parameter schema so the model knows the exact argument
        // names/types. Without this, the model guesses arg names and tools
        // fail with "missing required parameter(s)".
        ToolSchemaConverter.toObjectSchema(parametersJsonSchema)?.let { builder.parameters(it) }
        return builder.build()
    }

    @ApiStatus.Internal
    private fun ChatResponse.toAiChatResponse(): AiChatResponse {
        val aiMsg = this.aiMessage()
        val toolCalls = aiMsg.toolExecutionRequests()?.map { req ->
            AiToolCall(req.id(), req.name(), req.arguments())
        }
        val message = AiMessage.Assistant(
            content = aiMsg.text(),
            toolCalls = toolCalls
        )
        val finishReason = this.metadata()?.finishReason()?.toString()
        return AiChatResponse(message, finishReason)
    }
}

/**
 * Converts a generic JSON-schema map (the shape produced by
 * [com.itangcent.easyapi.core.ai.tools.AiTool.parametersSchema]) into a LangChain4j
 * [JsonObjectSchema] so the model receives the exact parameter names/types.
 *
 * Extracted from [LangChain4jAIService] so it is unit-testable without a real
 * `ChatModel`.
 */
internal object ToolSchemaConverter {

    /** Parse a JSON-schema string into a [JsonObjectSchema], or `null` on failure. */
    fun toObjectSchema(jsonSchema: String): JsonObjectSchema? {
        val map = runCatching {
            @Suppress("UNCHECKED_CAST")
            GsonUtils.fromJson(jsonSchema, Map::class.java) as? Map<String, Any?>
        }.getOrNull() ?: return null
        return runCatching { buildObjectSchema(map) }.getOrNull()
    }

    private fun buildObjectSchema(map: Map<String, Any?>): JsonObjectSchema {
        val builder = JsonObjectSchema.builder()
        (map["description"] as? String)?.let { builder.description(it) }
        @Suppress("UNCHECKED_CAST")
        val props = map["properties"] as? Map<String, Any?> ?: emptyMap()
        for ((propName, propDefAny) in props) {
            @Suppress("UNCHECKED_CAST")
            val propDef = propDefAny as? Map<String, Any?> ?: continue
            builder.addProperty(propName, buildElement(propDef))
        }
        val required = (map["required"] as? List<*>)?.mapNotNull { it as? String }
        if (!required.isNullOrEmpty()) builder.required(required)
        return builder.build()
    }

    private fun buildElement(def: Map<String, Any?>): JsonSchemaElement {
        val desc = def["description"] as? String
        return when ((def["type"] as? String)?.lowercase()) {
            "integer" -> JsonIntegerSchema.builder().apply { desc?.let { description(it) } }.build()
            "number" -> JsonNumberSchema.builder().apply { desc?.let { description(it) } }.build()
            "boolean" -> JsonBooleanSchema.builder().apply { desc?.let { description(it) } }.build()
            "array" -> {
                @Suppress("UNCHECKED_CAST")
                val items = def["items"] as? Map<String, Any?>
                JsonArraySchema.builder()
.apply { desc?.let { description(it) } }
.items(items?.let { buildElement(it) } ?: JsonStringSchema.builder().build())
.build()
            }
            "object" -> buildObjectSchema(def)
            else -> JsonStringSchema.builder().apply { desc?.let { description(it) } }.build()
        }
    }
}
