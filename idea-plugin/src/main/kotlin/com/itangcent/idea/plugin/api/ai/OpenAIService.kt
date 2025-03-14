package com.itangcent.idea.plugin.api.ai

/**
 * Implementation of AIService using OpenAI API
 *
 * Note: This implementation uses direct HTTP requests instead of the OpenAI client library
 * to avoid Kotlin version compatibility issues. The OpenAI client library requires Kotlin 2.0.0,
 * but the project uses Kotlin 1.8.0.
 */
import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.GsonUtils
import com.itangcent.http.RawContentType
import com.itangcent.http.contentType
import com.itangcent.idea.plugin.condition.ConditionOnSetting
import com.itangcent.idea.plugin.settings.helper.AISettingsHelper
import com.itangcent.idea.plugin.utils.AIUtils
import com.itangcent.intellij.extend.sub
import com.itangcent.intellij.logger.Logger
import com.itangcent.suv.http.HttpClientProvider
import java.io.IOException

/**
 * Implementation of AIService using OpenAI API
 */
@Singleton
@ConditionOnSetting("aiProvider", havingValue = "OpenAI")
open class OpenAIService : AIService {

    @Inject
    private lateinit var logger: Logger

    @Inject
    private lateinit var aiSettingsHelper: AISettingsHelper

    @Inject
    private lateinit var httpClientProvider: HttpClientProvider

    /**
     * Sends a prompt to the OpenAI service with a custom system message
     * @throws AIConfigurationException if the API key is not configured
     * @throws AIApiException if there's an error in the API response
     * @throws IOException if there's an error in the HTTP communication
     */
    override fun sendPrompt(systemMessage: String, userPrompt: String): String {
        val apiKey = getApiKey()
        if (apiKey.isNullOrBlank()) {
            logger.error("OpenAI API key is not configured")
            throw AIConfigurationException("OpenAI API key is not configured")
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
                "temperature" to 0.1
            )

            // Convert map to JSON string
            val requestBody = GsonUtils.toJson(requestBodyMap)

            // Get the HttpClient from the provider
            val httpClient = httpClientProvider.getHttpClient()

            // Create a POST request
            val httpRequest = httpClient.post("https://api.openai.com/v1/chat/completions")
                .contentType(RawContentType.APPLICATION_JSON)
                .header("Authorization", "Bearer $apiKey")
                .body(requestBody)

            // Execute the request and get the response
            val httpResponse = httpRequest.call()

            if (httpResponse.code() != 200) {
                // Try to parse error message from response
                val errorBody = httpResponse.string() ?: ""
                val errorMessage = try {
                    val errorJson = GsonUtils.parseToJsonTree(errorBody)
                    errorJson.sub("error")?.sub("message")?.asString ?: "Unknown error"
                } catch (_: Exception) {
                    "Error response: $errorBody"
                }

                logger.error("OpenAI API error: ${httpResponse.code()} - $errorMessage")
                throw AIApiException("OpenAI API returned status code ${httpResponse.code()} - $errorMessage")
            }

            // Parse the response to extract the AI's message
            val responseBody = httpResponse.string() ?: throw AIApiException("Empty response from OpenAI API")

            // Parse JSON response
            val jsonElement = GsonUtils.parseToJsonTree(responseBody)
            val content = jsonElement.sub("choices")?.asJsonArray?.firstOrNull()
                ?.asJsonObject?.sub("message")?.sub("content")?.asString
            
            return content?.let { AIUtils.cleanMarkdownCodeBlocks(it) }
                ?: throw AIApiException("Could not parse response from OpenAI API")
        } catch (e: AIException) {
            // Re-throw AI exceptions
            throw e
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