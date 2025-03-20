package com.itangcent.ai

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.GsonUtils
import com.itangcent.http.RawContentType
import com.itangcent.http.contentType
import com.itangcent.idea.plugin.condition.ConditionOnSetting
import com.itangcent.idea.plugin.settings.helper.AISettingsHelper
import com.itangcent.intellij.extend.sub
import com.itangcent.intellij.logger.Logger
import com.itangcent.suv.http.HttpClientProvider
import java.io.IOException

/**
 * Implementation of AIService using DeepSeek API
 */
@Singleton
@ConditionOnSetting("aiProvider", havingValue = "DeepSeek")
open class DeepSeekService : AIService {

    @Inject
    private lateinit var logger: Logger

    @Inject
    private lateinit var aiSettingsHelper: AISettingsHelper

    @Inject
    private lateinit var httpClientProvider: HttpClientProvider

    /**
     * Sends a prompt to the DeepSeek service with a custom system message
     * @throws AIConfigurationException if the API key is not configured
     * @throws AIApiException if there's an error in the API response
     * @throws IOException if there's an error in the HTTP communication
     */
    override fun sendPrompt(systemMessage: String, userPrompt: String): String {
        val apiKey = getApiKey()
        if (apiKey.isNullOrBlank()) {
            logger.error("DeepSeek API key is not configured")
            throw AIConfigurationException("DeepSeek API key is not configured")
        }

        try {
            val requestBodyMap = mapOf(
                "model" to getModelName(),
                "messages" to listOf(
                    mapOf(
                        "role" to "system",
                        "content" to systemMessage
                    ),
                    mapOf(
                        "role" to "user",
                        "content" to userPrompt
                    )
                ),
                "stream" to false
            )

            // Convert map to JSON string
            val requestBody = GsonUtils.toJson(requestBodyMap)

            // Get the HttpClient from the provider
            val httpClient = httpClientProvider.getHttpClient()

            // Create a POST request
            val httpRequest = httpClient.post("https://api.deepseek.com/chat/completions")
                .contentType(RawContentType.APPLICATION_JSON)
                .header("Authorization", "Bearer $apiKey")
                .body(requestBody)

            // Execute the request and get the response
            val httpResponse = httpRequest.call()

            if (httpResponse.code() != 200) {
                val errorMessage = "DeepSeek API returned status code ${httpResponse.code()}: ${httpResponse.string()}"
                logger.error(errorMessage)
                throw AIApiException(errorMessage)
            }

            // Parse the response to extract the AI's message
            val responseBody = httpResponse.string() ?: throw AIApiException("Empty response from DeepSeek API")

            // Parse JSON response
            val jsonElement = GsonUtils.parseToJsonTree(responseBody)
            val content = jsonElement.sub("choices")?.asJsonArray?.firstOrNull()
                ?.asJsonObject?.sub("message")?.sub("content")?.asString
            return content ?: throw AIApiException("Could not parse response from DeepSeek API")
        } catch (e: AIException) {
            // Re-throw AI exceptions
            throw e
        } catch (e: Exception) {
            logger.traceError("Error calling DeepSeek API", e)
            throw AIApiException("Error calling DeepSeek API: ${e.message}", e)
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
        // Use the configured model from settings or default to "deepseek-chat"
        return aiSettingsHelper.aiModel ?: "deepseek-chat"
    }
} 