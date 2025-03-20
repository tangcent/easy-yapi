package com.itangcent.ai

import com.google.inject.ProvidedBy
import com.google.inject.Singleton
import com.itangcent.spi.SpiSingleBeanProvider

/**
 * Interface for AI service operations
 */
@ProvidedBy(AIServiceProvider::class)
interface AIService {

    /**
     * Sends a prompt to the AI service and returns the response
     * @param prompt The user prompt to send to the AI service
     * @throws AIException if there's an error with the AI service
     */
    fun sendPrompt(prompt: String): String {
        return sendPrompt(AIMessages.DEFAULT_SYSTEM_MESSAGE, prompt)
    }

    /**
     * Sends a prompt to the AI service with a specific system message and returns the response
     * @param systemMessage The system message that defines the AI's behavior/role
     * @param userPrompt The user prompt to send to the AI service
     * @throws AIConfigurationException if there's an issue with the AI service configuration
     * @throws AIApiException if there's an error in the AI service API response
     */
    fun sendPrompt(systemMessage: String, userPrompt: String): String {
        // Default implementation delegates to the simpler method
        // Implementations should override this for better efficiency
        return sendPrompt(userPrompt)
    }
}

@Singleton
class AIServiceProvider : SpiSingleBeanProvider<AIService>()