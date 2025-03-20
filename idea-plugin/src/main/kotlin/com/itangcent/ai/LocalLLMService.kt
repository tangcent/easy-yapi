package com.itangcent.ai

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.idea.plugin.condition.ConditionOnSetting
import com.itangcent.idea.plugin.settings.helper.AISettingsHelper
import com.itangcent.suv.http.HttpClientProvider


/**
 * Implementation of AIService that interfaces with a local LLM server.
 */
@Singleton
@ConditionOnSetting("aiProvider", havingValue = "LocalLLM")
open class LocalLLMService : AIService {

    companion object

    @Inject
    private lateinit var aiSettingsHelper: AISettingsHelper

    @Inject
    private lateinit var httpClientProvider: HttpClientProvider

    private val rawLocalLLMService: LocalLLMClient by lazy {
        LocalLLMClient(getServerUrl(), getModelName(), httpClientProvider.getHttpClient())
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
        return rawLocalLLMService.sendPrompt(systemMessage, userPrompt)
    }

    /**
     * Retrieves the configured local LLM server URL from settings.
     *
     * @return The configured server URL
     * @throws AIConfigurationException if the URL is not configured
     */
    private fun getServerUrl(): String {
        return aiSettingsHelper.aiLocalServerUrl
            ?: throw AIConfigurationException("Local LLM server URL is not configured")
    }

    /**
     * Retrieves the configured model name from settings or returns a default value.
     *
     * @return The configured model name or "local-model" as default
     */
    private fun getModelName(): String {
        return aiSettingsHelper.aiModel ?: "local-model"
    }
}

