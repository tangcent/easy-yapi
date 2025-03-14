package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.idea.plugin.settings.SettingBinder

/**
 * Helper class for accessing AI-related settings
 */
@Singleton
class AISettingsHelper {

    @Inject
    private lateinit var settingBinder: SettingBinder

    /**
     * Check if AI-based inference is enabled in settings
     */
    val aiEnable: Boolean get() = settingBinder.read().run { aiEnable && aiModel != null && aiToken != null }

    /**
     * Get the AI model type from settings
     */
    val aiModel: String? get() = settingBinder.read().aiModel

    /**
     * Get the AI API token from settings
     */
    val aiToken: String? get() = settingBinder.read().aiToken

    /**
     * Get the AI provider from settings
     */
    val aiProvider: String? get() = settingBinder.read().aiProvider
    
    /**
     * Check if AI API response caching is enabled
     */
    val aiEnableCache: Boolean get() = settingBinder.read().aiEnableCache
} 