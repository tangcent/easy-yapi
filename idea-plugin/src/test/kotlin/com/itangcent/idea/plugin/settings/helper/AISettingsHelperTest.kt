package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test case of [AISettingsHelper]
 */
internal class AISettingsHelperTest : SettingsHelperTest() {

    @Inject
    private lateinit var aiSettingsHelper: AISettingsHelper

    @Test
    fun testAiEnable() {
        settings.aiEnable = false
        assertFalse(aiSettingsHelper.aiEnable)
        settings.aiEnable = true
        assertFalse(aiSettingsHelper.aiEnable)
        settings.aiModel = "gpt-4"
        settings.aiToken = "test-token-123"
        assertTrue(aiSettingsHelper.aiEnable)
    }

    @Test
    fun testAiModel() {
        settings.aiModel = null
        assertEquals(null, aiSettingsHelper.aiModel)
        settings.aiModel = "gpt-4"
        assertEquals("gpt-4", aiSettingsHelper.aiModel)
        settings.aiModel = "claude-3"
        assertEquals("claude-3", aiSettingsHelper.aiModel)
    }

    @Test
    fun testAiToken() {
        settings.aiToken = null
        assertEquals(null, aiSettingsHelper.aiToken)
        settings.aiToken = "test-token-123"
        assertEquals("test-token-123", aiSettingsHelper.aiToken)
    }

    @Test
    fun testAiProvider() {
        settings.aiProvider = null
        assertEquals(null, aiSettingsHelper.aiProvider)
        settings.aiProvider = "openai"
        assertEquals("openai", aiSettingsHelper.aiProvider)
        settings.aiProvider = "anthropic"
        assertEquals("anthropic", aiSettingsHelper.aiProvider)
    }

    @Test
    fun testAiEnableCache() {
        settings.aiEnableCache = false
        assertFalse(aiSettingsHelper.aiEnableCache)
        settings.aiEnableCache = true
        assertTrue(aiSettingsHelper.aiEnableCache)
    }

    @Test
    fun testTranslationEnabled() {
        // When AI is disabled, translation should be disabled
        settings.aiEnable = false
        settings.aiTranslationEnabled = true
        settings.aiTranslationTargetLanguage = "en"
        assertFalse(aiSettingsHelper.translationEnabled)
        
        // When AI is enabled but translation is disabled
        settings.aiEnable = true
        settings.aiModel = "gpt-4"
        settings.aiToken = "test-token-123"
        settings.aiTranslationEnabled = false
        assertFalse(aiSettingsHelper.translationEnabled)
        
        // When AI and translation are enabled but no target language
        settings.aiTranslationEnabled = true
        settings.aiTranslationTargetLanguage = null
        assertFalse(aiSettingsHelper.translationEnabled)
        
        // When all required settings are properly configured
        settings.aiTranslationTargetLanguage = "en"
        assertTrue(aiSettingsHelper.translationEnabled)
    }

    @Test
    fun testTranslationTargetLanguageName() {
        // When no language code is set
        settings.aiTranslationTargetLanguage = null
        assertEquals(null, aiSettingsHelper.translationTargetLanguageName)
        
        // When English language code is set
        settings.aiTranslationTargetLanguage = "en"
        assertEquals("English", aiSettingsHelper.translationTargetLanguageName)
        
        // When Chinese language code is set
        settings.aiTranslationTargetLanguage = "zh"
        assertEquals("Chinese", aiSettingsHelper.translationTargetLanguageName)
        
        // When Japanese language code is set
        settings.aiTranslationTargetLanguage = "ja"
        assertEquals("Japanese", aiSettingsHelper.translationTargetLanguageName)
    }
} 