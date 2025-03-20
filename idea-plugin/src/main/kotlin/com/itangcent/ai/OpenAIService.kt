package com.itangcent.ai

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.common.logger.traceError
import com.itangcent.idea.plugin.condition.ConditionOnSetting
import com.itangcent.idea.plugin.settings.helper.AISettingsHelper
import com.itangcent.idea.plugin.settings.helper.HttpSettingsHelper
import com.itangcent.intellij.logger.Logger
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.errors.OpenAIException
import com.openai.models.ChatCompletionCreateParams


/**
 * Implementation of AIService using the official OpenAI Java SDK
 */
@Singleton
@ConditionOnSetting("aiProvider", havingValue = "OpenAI")
open class OpenAIService : AIService {

    @Inject
    private lateinit var logger: Logger

    @Inject
    private lateinit var aiSettingsHelper: AISettingsHelper

    @Inject
    private lateinit var httpSettingsHelper: HttpSettingsHelper

    // Create OpenAI client with timeout
    val client: OpenAIClient by lazy {
        OpenAIOkHttpClient.builder()
            .apiKey(aiSettingsHelper.aiToken!!)
            .timeout(httpSettingsHelper.httpTimeOut())
            .build()
    }

    /**
     * Sends a prompt to the OpenAI service with a custom system message
     * @throws AIConfigurationException if the API key is not configured
     * @throws AIApiException if there's an error in the API response
     */
    override fun sendPrompt(systemMessage: String, userPrompt: String): String {
        val apiKey = getApiKey()
        if (apiKey.isNullOrBlank()) {
            logger.error("OpenAI API key is not configured")
            throw AIConfigurationException("OpenAI API key is not configured")
        }

        try {
            val params = ChatCompletionCreateParams.builder()
                .addSystemMessage(systemMessage)
                .addUserMessage(userPrompt)
                .model(getModelName())
                .build()

            // Execute the request
            val response = client.chat().completions().create(params)

            // Extract and return the content
            val content = response.choices().firstOrNull()?.message()?.content()
                ?.orElse("")

            return content ?: throw AIApiException("Empty response from OpenAI API")
        } catch (e: OpenAIException) {
            logger.traceError("OpenAI API error: ${e.message}", e)
            throw AIApiException("OpenAI API error: ${e.message}", e)
        } catch (e: Exception) {
            logger.traceError("Error calling OpenAI API", e)
            throw AIApiException("Error calling OpenAI API: ${e.message}", e)
        }
    }

    /**
     * Gets the API key from settings
     */
    private fun getApiKey(): String? {
        return aiSettingsHelper.aiToken
    }

    /**
     * Gets the model name to use from settings or default
     */
    private fun getModelName(): String {
        // Use the configured model from settings or default to "gpt-3.5-turbo"
        return aiSettingsHelper.aiModel ?: "gpt-3.5-turbo"
    }
}