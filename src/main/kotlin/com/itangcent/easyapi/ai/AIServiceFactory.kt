package com.itangcent.easyapi.ai

import dev.langchain4j.model.anthropic.AnthropicChatModel
import dev.langchain4j.model.azure.AzureOpenAiChatModel
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import java.time.Duration

/**
 * Factory that builds a provider-specific [ChatModel] and wraps it in a
 * [LangChain4jAIService].
 *
 * This is the **only** place in the codebase that references provider-specific
 * LangChain4j classes. All other plugin code depends solely on [AIService].
 */
object AIServiceFactory {

    fun create(settings: AiRuntimeConfig): AIService {
        val timeout = Duration.ofSeconds(settings.requestTimeoutSec.toLong())
        val model = when (settings.provider) {
            AiProvider.ANTHROPIC -> AnthropicChatModel.builder()
.apiKey(settings.apiKey)
.modelName(settings.model)
.timeout(timeout)
.build()

            AiProvider.GEMINI -> GoogleAiGeminiChatModel.builder()
.apiKey(settings.apiKey)
.modelName(settings.model)
.timeout(timeout)
.build()

            AiProvider.OLLAMA -> OllamaChatModel.builder()
.baseUrl(settings.baseUrl)
.modelName(settings.model)
.timeout(timeout)
.build()

            AiProvider.AZURE_OPENAI -> AzureOpenAiChatModel.builder()
.apiKey(settings.apiKey)
.endpoint(settings.baseUrl)
.deploymentName(settings.model)
.timeout(timeout)
.build()

            // All remaining providers speak the OpenAI Chat Completions protocol
            // and are served by the OpenAI client with a provider-specific baseUrl.
            else -> OpenAiChatModel.builder()
                // Local / keyless servers (Ollama-compatible hosts, LiteLLM proxy,
                // LM Studio) still need a non-blank key for the OpenAI client.
.apiKey(settings.apiKey.ifBlank { "dummy" })
.baseUrl(settings.baseUrl)
.modelName(settings.model)
.timeout(timeout)
.build()
        }
        return LangChain4jAIService(model, settings)
    }

    /**
     * Creates an [AIService] for the given project, or returns `null` if AI
     * settings are not yet configured (provider not selected, required key
     * missing, etc.).
     */
    fun createForProject(project: com.intellij.openapi.project.Project): AIService? {
        val settings = AiRuntimeConfig.load(project) ?: return null
        return create(settings)
    }
}
