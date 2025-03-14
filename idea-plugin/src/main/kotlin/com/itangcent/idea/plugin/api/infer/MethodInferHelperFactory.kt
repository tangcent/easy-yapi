package com.itangcent.idea.plugin.api.infer

import com.google.inject.Inject
import com.google.inject.Provider
import com.google.inject.Singleton
import com.itangcent.common.logger.Log
import com.itangcent.common.logger.traceError
import com.itangcent.idea.plugin.settings.helper.AISettingsHelper
import com.itangcent.intellij.logger.Logger

/**
 * Factory for creating the appropriate MethodInferHelper based on settings
 */
@Singleton
class MethodInferHelperFactory {

    companion object : Log()

    @Inject
    private lateinit var logger: Logger

    @Inject
    private lateinit var aiSettingsHelper: AISettingsHelper

    @Inject
    private lateinit var defaultMethodInferHelperProvider: Provider<DefaultMethodInferHelper>

    @Inject
    private lateinit var aiMethodInferHelperProvider: Provider<AIMethodInferHelper>

    /**
     * Get the appropriate MethodInferHelper based on settings
     */
    fun getMethodInferHelper(): MethodInferHelper {
        // Check if AI inference is enabled in settings
        if (aiSettingsHelper.aiEnable) {
            try {
                LOG.info("Using AI-based method inference")
                return aiMethodInferHelperProvider.get()
            } catch (e: Exception) {
                logger.traceError("Failed to initialize AI-based method inference, falling back to default", e)
            }
        }

        // Default to the standard inference method
        return defaultMethodInferHelperProvider.get()
    }
} 