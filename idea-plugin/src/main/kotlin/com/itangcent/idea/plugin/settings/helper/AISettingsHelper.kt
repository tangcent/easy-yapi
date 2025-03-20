package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.common.constant.Language
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
     * Get the local LLM server URL from settings
     */
    val aiLocalServerUrl: String? get() = settingBinder.read().aiLocalServerUrl

    /**
     * Get the AI provider from settings
     */
    val aiProvider: String? get() = settingBinder.read().aiProvider

    /**
     * Check if AI API response caching is enabled
     */
    val aiEnableCache: Boolean get() = settingBinder.read().aiEnableCache

    /**
     * Check if API translation is enabled
     */
    val translationEnabled: Boolean
        get() = settingBinder.read().run {
            aiEnable && aiTranslationEnabled && aiTranslationTargetLanguage != null
        }

    /**
     * Check if AI method inference is enabled
     */
    val methodInferEnabled: Boolean
        get() = settingBinder.read().run {
            aiEnable && aiMethodInferEnabled
        }

    /**
     * Get the target language for API translation
     * Returns the language name (e.g., "English") instead of the language code (e.g., "en")
     */
    val translationTargetLanguageName: String?
        get() {
            val languageCode = settingBinder.read().aiTranslationTargetLanguage ?: return null
            return Language.getNameFromCode(languageCode)
        }
} 