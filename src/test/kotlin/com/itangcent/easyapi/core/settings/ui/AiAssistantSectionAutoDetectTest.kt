package com.itangcent.easyapi.core.settings.ui

import com.itangcent.easyapi.core.ai.AiProvider
import com.itangcent.easyapi.core.ai.credentials.DetectionResult
import com.itangcent.easyapi.core.settings.module.AiSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

/**
 * Tests for the Auto-detect button in [AiAssistantSection].
 *
 * The background scan is bypassed by calling [AiAssistantSection.applyDetectionResultForTest]
 * directly — we already cover the scanner itself in `CredentialScannerTest`.
 * Here we only assert UI behaviour: field pre-fill, provider switch, and the
 * "respect user edits" rule.
 */
class AiAssistantSectionAutoDetectTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testHitPreFillsFields() {
        val section = freshSection()
        section.applyDetectionResultForTest(
            DetectionResult.Hit(
                provider = AiProvider.ANTHROPIC,
                sourceLabel = "env var ANTHROPIC_API_KEY",
                apiKey = "sk-ant-detected",
                baseUrl = "https://api.anthropic.com",
                model = "claude-3-5-haiku-latest"
            )
        )
        assertEquals(AiProvider.ANTHROPIC, section.currentProviderForTest())
        assertEquals("sk-ant-detected", section.apiKeyText())
        assertEquals("https://api.anthropic.com", section.baseUrlText())
        assertEquals("claude-3-5-haiku-latest", section.modelText())
        // The result is surfaced in the inline status label.
        assertTrue(
            "status should mention the detected provider; got: ${section.statusTextForTest()}",
            section.statusTextForTest().contains("Anthropic")
        )
    }

    fun testHitFallsBackToProviderDefaultBaseUrl() {
        val section = freshSection()
        section.applyDetectionResultForTest(
            DetectionResult.Hit(
                provider = AiProvider.OPENAI,
                sourceLabel = "~/.openai/api_key",
                apiKey = "sk-detected",
                baseUrl = null, // no explicit baseUrl
                model = null
            )
        )
        assertEquals(AiProvider.OPENAI, section.currentProviderForTest())
        assertEquals("sk-detected", section.apiKeyText())
        // No explicit baseUrl + no user edit → fall back to provider default.
        assertEquals(AiProvider.OPENAI.defaultBaseUrl, section.baseUrlText())
        assertEquals(AiProvider.OPENAI.defaultModel, section.modelText())
    }

    fun testLocalServerHitHasNullApiKey() {
        val section = freshSection()
        section.applyDetectionResultForTest(
            DetectionResult.Hit(
                provider = AiProvider.OLLAMA,
                sourceLabel = "Ollama on :11434",
                apiKey = null,
                baseUrl = "http://localhost:11434/v1",
                model = null
            )
        )
        assertEquals(AiProvider.OLLAMA, section.currentProviderForTest())
        // No key — Ollama doesn't need one.
        assertEquals("", section.apiKeyText())
        assertEquals("http://localhost:11434/v1", section.baseUrlText())
    }

    fun testUserEditedFieldsAreRespected() {
        val section = freshSection()
        // Simulate the user typing a different key first.
        section.setBaseUrl("http://my-custom-litellm:4000/v1")
        section.setModel("my-custom-model")
        // API key field has no public setter — drive it through a Hit first,
        // then assert a second Hit doesn't overwrite the first.
        section.applyDetectionResultForTest(
            DetectionResult.Hit(
                provider = AiProvider.OPENAI,
                sourceLabel = "env var OPENAI_API_KEY",
                apiKey = "sk-first",
                baseUrl = "https://api.openai.com/v1",
                model = "gpt-4o-mini"
            )
        )
        // Now run a second scan — it must NOT clobber the user's manual edits.
        section.applyDetectionResultForTest(
            DetectionResult.Hit(
                provider = AiProvider.ANTHROPIC,
                sourceLabel = "env var ANTHROPIC_API_KEY",
                apiKey = "sk-second",
                baseUrl = "https://api.anthropic.com",
                model = "claude-3-5-haiku-latest"
            )
        )
        // Provider switched (combo isn't a "user edit" target), but the
        // user-typed base URL + model survive.
        assertEquals(AiProvider.ANTHROPIC, section.currentProviderForTest())
        assertEquals("http://my-custom-litellm:4000/v1", section.baseUrlText())
        assertEquals("my-custom-model", section.modelText())
    }

    fun testMissLeavesFieldsEmpty() {
        val section = freshSection()
        section.applyDetectionResultForTest(DetectionResult.Miss)
        // Default provider is OPENAI on a fresh section, but base URL / model
        // should NOT be pre-filled because no detection occurred.
        assertEquals("", section.baseUrlText())
        assertEquals("", section.modelText())
        assertEquals("", section.apiKeyText())
        // The "no credentials" outcome is shown inline.
        assertTrue(
            "status should mention no credentials; got: ${section.statusTextForTest()}",
            section.statusTextForTest().contains("No local AI credentials")
        )
    }

    fun testMultipleFoundPreFillsWithPrimary() {
        val primary = DetectionResult.Hit(
            provider = AiProvider.OPENAI,
            sourceLabel = "env var OPENAI_API_KEY",
            apiKey = "sk-primary",
            baseUrl = null,
            model = null
        )
        val secondary = DetectionResult.Hit(
            provider = AiProvider.ANTHROPIC,
            sourceLabel = "~/.claude/credentials.json",
            apiKey = "sk-ant-secondary",
            baseUrl = null,
            model = null
        )
        val section = freshSection()
        section.applyDetectionResultForTest(
            DetectionResult.MultipleFound(primary = primary, others = listOf(secondary))
        )
        assertEquals(AiProvider.OPENAI, section.currentProviderForTest())
        assertEquals("sk-primary", section.apiKeyText())
    }

    fun testAutoDetectButtonInitialLabel() {
        val section = freshSection()
        assertEquals("Auto-detect", section.autoDetectButtonLabel())
        assertTrue(section.isAutoDetectButtonEnabled())
    }

    private fun freshSection(): AiAssistantSection {
        val section = AiAssistantSection()
        section.resetFrom(AiSettings())
        return section
    }
}
