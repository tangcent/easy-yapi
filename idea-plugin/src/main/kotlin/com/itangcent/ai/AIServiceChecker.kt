package com.itangcent.ai

import com.itangcent.common.logger.Log
import com.itangcent.common.logger.traceError

/**
 * A utility class for checking the health and availability of AI services.
 * This class provides methods to verify if AI services are operational and can handle requests.
 */
object AIServiceHealthChecker : Log() {
    /**
     * Checks if the AI service is available and can handle requests.
     * For regular AI services, it verifies by sending a simple test prompt.
     * For Local LLM clients, it checks if there are any available models.
     *
     * @return true if the service is available and can handle requests, false otherwise
     */
    fun AIService.isAvailable(): Boolean {
        if (this is LocalLLMClient) {
            return this.hasAvailableModels()
        }
        return try {
            val response =
                sendPrompt(systemMessage = "Answer Question", userPrompt = "Please respond with exactly 'YES'")
            response.contains("YES", ignoreCase = true)
        } catch (e: Exception) {
            LOG.traceError("Failed to check AI service", e)
            false
        }
    }

    /**
     * Checks if the Local LLM client has any available models.
     * This is used to verify if the local LLM service is properly configured and ready to use.
     *
     * @return true if there are available models, false otherwise
     */
    fun LocalLLMClient.hasAvailableModels(): Boolean {
        try {
            val availableModels = this.getAvailableModels()
            return availableModels.isNotEmpty()
        } catch (e: Exception) {
            LOG.traceError("Failed to check AI service", e)
            return false
        }
    }
}
