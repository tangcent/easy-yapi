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
} 