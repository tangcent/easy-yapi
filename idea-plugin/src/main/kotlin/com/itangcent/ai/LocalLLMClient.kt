package com.itangcent.ai

import com.itangcent.common.utils.GsonUtils
import com.itangcent.http.HttpClient
import com.itangcent.http.RawContentType
import com.itangcent.http.contentType
import com.itangcent.idea.plugin.utils.AIUtils
import com.itangcent.intellij.extend.sub

/**
 * Client implementation for interacting with a local LLM server.
 * This class handles the direct communication with the LLM server API.
 */
class LocalLLMClient(
    private val serverUrl: String,
    private val modelName: String,
    private val httpClient: HttpClient
) : AIService {

    companion object {
        private const val CHAT_COMPLETIONS_ENDPOINT = "/chat/completions"
        private const val MODELS_ENDPOINT = "/models"
    }

    /**
     * Sends a prompt to the local LLM service with a custom system message.
     *
     * @param systemMessage The system message that sets the context for the LLM
     * @param userPrompt The user's input prompt to be processed
     * @return The LLM's response as a string
     * @throws AIConfigurationException if the local LLM server URL is not configured
     * @throws AIApiException if there's an error in the API response or communication
     */
    override fun sendPrompt(systemMessage: String, userPrompt: String): String {
        val fullUrl = "$serverUrl$CHAT_COMPLETIONS_ENDPOINT"
        try {
            val requestBodyMap = mapOf(
                "messages" to listOf(
                    mapOf("role" to "system", "content" to systemMessage),
                    mapOf("role" to "user", "content" to userPrompt)
                ),
                "model" to modelName,
                "stream" to false
            )

            val requestBody = GsonUtils.toJson(requestBodyMap)
            val httpRequest = httpClient.post(fullUrl)
                .contentType(RawContentType.APPLICATION_JSON)
                .body(requestBody)

            val httpResponse = httpRequest.call()

            if (httpResponse.code() != 200) {
                val errorMessage =
                    "Local LLM server returned status code ${httpResponse.code()}: ${httpResponse.string()}"
                throw AIApiException(errorMessage)
            }

            val responseBody = httpResponse.string() ?: throw AIApiException("Empty response from Local LLM server")
            val jsonElement = GsonUtils.parseToJsonTree(responseBody)
            val content = jsonElement.sub("choices")?.asJsonArray?.firstOrNull()
                ?.asJsonObject?.sub("message")?.sub("content")?.asString
            val errMsg = jsonElement.sub("error")?.asString
            return content?.let { AIUtils.cleanMarkdownCodeBlocks(it) }
                ?: throw AIApiException(errMsg ?: "Could not parse response from Local LLM server")
        } catch (e: AIException) {
            throw e
        } catch (e: Exception) {
            throw AIApiException("Error calling Local LLM server: ${e.message}", e)
        }
    }

    /**
     * Retrieves the list of available models from the local LLM server.
     *
     * @return List of model IDs available on the server
     * @throws AIApiException if there's an error in the API response or communication
     */
    fun getAvailableModels(): List<String> {
        val url = "$serverUrl$MODELS_ENDPOINT"

        try {
            val response = httpClient.get(url).call()

            if (response.code() != 200) {
                throw AIApiException("Failed to get models: ${response.code()}")
            }

            val responseBody = response.string() ?: throw AIApiException("Empty response from server")
            val jsonElement = GsonUtils.parseToJsonTree(responseBody)
            val dataArray = jsonElement.sub("data")?.asJsonArray
                ?: throw AIApiException("Invalid response format: missing 'data' array")

            return dataArray.mapNotNull { modelObj ->
                modelObj.asJsonObject.sub("id")?.asString
            }
        } catch (e: Exception) {
            throw AIApiException("Error getting models: ${e.message}", e)
        }
    }
}