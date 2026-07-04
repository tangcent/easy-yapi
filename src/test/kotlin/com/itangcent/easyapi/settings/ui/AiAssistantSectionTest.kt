package com.itangcent.easyapi.settings.ui

import com.itangcent.easyapi.ai.AiProvider
import com.itangcent.easyapi.ai.AiRuntimeConfig
import com.itangcent.easyapi.ai.AIService
import com.itangcent.easyapi.ai.AiChatRequest
import com.itangcent.easyapi.ai.AiChatResponse
import com.itangcent.easyapi.settings.module.AiSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AiAssistantSectionTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testRoundTripDefaults() {
        val section = AiAssistantSection()
        val settings = AiSettings()
        section.resetFrom(settings)
        assertFalse("fresh settings should not be modified", section.isModified(settings))
        section.applyTo(settings)
        assertEquals(AiProvider.OPENAI.name, settings.aiProvider)
        assertEquals(60, settings.aiRequestTimeoutSec)
        assertEquals(100, settings.aiMaxRequests)
        // Default context window is the provider default (128_000).
        assertEquals(128_000, settings.aiContextWindow)
    }

    fun testProviderSwitchPreFillsDefaults() {
        val section = AiAssistantSection()
        val settings = AiSettings()
        section.resetFrom(settings)

        // Simulate selecting OLLAMA (no API key required, has default base URL + model)
        section.selectProvider(AiProvider.OLLAMA)
        assertTrue("switching provider should mark modified", section.isModified(settings))

        section.applyTo(settings)
        assertEquals(AiProvider.OLLAMA.name, settings.aiProvider)
        assertEquals(AiProvider.OLLAMA.defaultBaseUrl, settings.aiBaseUrl)
        assertEquals(AiProvider.OLLAMA.defaultModel, settings.aiModel)
    }

    /**
     * Regression: a programmatic provider switch used to latch the
     * `userEdited*` flags (via the fields' own document listeners), so the
     * pre-fill only stuck on the *first* switch. Switching providers twice
     * must keep pre-filling base URL + model until the user edits a field.
     */
    fun testProviderSwitchTwiceKeepsPreFilling() {
        val section = AiAssistantSection()
        val settings = AiSettings()
        section.resetFrom(settings)

        // First switch: OLLAMA defaults appear.
        section.selectProvider(AiProvider.OLLAMA)
        assertEquals(AiProvider.OLLAMA.defaultBaseUrl, section.baseUrlText())
        assertEquals(AiProvider.OLLAMA.defaultModel, section.modelText())

        // Second switch: GEMINI defaults must also appear (the bug froze the fields).
        section.selectProvider(AiProvider.GEMINI)
        assertEquals(AiProvider.GEMINI.defaultBaseUrl ?: "", section.baseUrlText())
        assertEquals(AiProvider.GEMINI.defaultModel ?: "", section.modelText())
    }

    /**
     * A real manual edit is sticky: once the user types a custom base URL,
     * subsequent provider switches leave it alone.
     */
    fun testManualEditSticksAcrossProviderSwitch() {
        val section = AiAssistantSection()
        val settings = AiSettings()
        section.resetFrom(settings)

        section.selectProvider(AiProvider.CUSTOM)
        section.setBaseUrl("http://my-litellm:4000/v1") // user edit
        section.selectProvider(AiProvider.OPENAI)

        assertEquals(
            "manually edited base URL must survive a provider switch",
            "http://my-litellm:4000/v1",
            section.baseUrlText()
        )
    }

    fun testCustomFieldsRoundTrip() {
        val section = AiAssistantSection()
        val settings = AiSettings()
        section.resetFrom(settings)

        section.selectProvider(AiProvider.CUSTOM)
        section.setBaseUrl("http://my-litellm:4000/v1")
        section.setModel("gpt-4o")
        section.setTimeoutSec(120)
        section.setMaxRequests(15)

        assertTrue("custom values should mark modified", section.isModified(settings))

        val roundTripped = AiSettings()
        section.applyTo(roundTripped)
        section.resetFrom(roundTripped)
        assertFalse("after round-trip nothing modified", section.isModified(roundTripped))
        assertEquals(AiProvider.CUSTOM.name, roundTripped.aiProvider)
        assertEquals("http://my-litellm:4000/v1", roundTripped.aiBaseUrl)
        assertEquals("gpt-4o", roundTripped.aiModel)
        assertEquals(120, roundTripped.aiRequestTimeoutSec)
        assertEquals(15, roundTripped.aiMaxRequests)
    }

    fun testContextWindowRoundTrip() {
        val section = AiAssistantSection()
        val settings = AiSettings()
        section.resetFrom(settings)

        section.setContextWindow(200_000)

        assertTrue("a non-default context window should mark modified",
            section.isModified(settings))

        val roundTripped = AiSettings()
        section.applyTo(roundTripped)
        section.resetFrom(roundTripped)
        assertFalse("after round-trip nothing modified", section.isModified(roundTripped))
        assertEquals(200_000, roundTripped.aiContextWindow)
    }

    // --- API key: provider switch clears it ---

    /**
     * Switching provider always clears the API key — a key is provider-specific
     * and reusing it would just produce a 401.
     */
    fun testProviderSwitchClearsApiKey() {
        val section = AiAssistantSection()
        val settings = AiSettings()
        section.resetFrom(settings)

        section.setApiKey("sk-original")
        assertEquals("sk-original", section.apiKeyText())

        section.selectProvider(AiProvider.OLLAMA)
        assertEquals("API key should be cleared on provider switch", "", section.apiKeyText())
    }

    /**
     * The clear must not latch `userEditedApiKey`: a later Auto-detect hit
     * should still be able to pre-fill the key.
     */
    fun testProviderSwitchClearDoesNotLatchEditedFlag() {
        val section = AiAssistantSection()
        val settings = AiSettings()
        section.resetFrom(settings)

        section.selectProvider(AiProvider.OLLAMA) // clears (empty) key, must not latch
        section.setApiKey("sk-after")
        assertEquals("a manual edit after a switch still works", "sk-after", section.apiKeyText())
    }

    // --- API key: eye-icon reveal toggle ---

    fun testApiKeyMaskedByDefault() {
        val section = AiAssistantSection()
        section.resetFrom(AiSettings())
        assertFalse("API key must be masked by default", section.isApiKeyRevealedForTest())
    }

    fun testRevealToggleShowsThenHidesApiKey() {
        val section = AiAssistantSection()
        section.resetFrom(AiSettings())
        section.setApiKey("sk-secret")

        section.toggleRevealApiKeyForTest()
        assertTrue("key should be revealed after toggling", section.isApiKeyRevealedForTest())
        assertEquals("reveal must not alter the value", "sk-secret", section.apiKeyText())

        section.toggleRevealApiKeyForTest()
        assertFalse("key should be re-masked on second toggle", section.isApiKeyRevealedForTest())
        assertEquals("re-mask must not alter the value", "sk-secret", section.apiKeyText())
    }

    // --- Test Connection ---

    fun testTestConnectionSuccess() {
        val section = AiAssistantSection()
        val latch = CountDownLatch(1)
        var capturedResult: Result<String>? = null

        section.aiServiceFactory = { _ -> FakeTestConnectionAIService(Result.success("pong")) }
        section.testConnectionResultHandler = { result ->
            capturedResult = result
            latch.countDown()
        }

        // Fill in some fields so the settings are non-default.
        section.selectProvider(AiProvider.OLLAMA)
        section.setBaseUrl("http://localhost:11434")
        section.setModel("llama3")

        // Trigger the test connection (simulates button click).
        section.triggerTestConnectionForTest()

        assertTrue("test connection should complete within timeout",
            latch.await(10, TimeUnit.SECONDS))
        assertTrue("result should be success", capturedResult!!.isSuccess)
        assertEquals("pong", capturedResult!!.getOrNull())
        // Button re-enabled after completion.
        assertTrue("button should be re-enabled", section.isTestConnectionButtonEnabled())
        assertEquals("Test Connection", section.testConnectionButtonLabel())
    }

    fun testTestConnectionFailure() {
        val section = AiAssistantSection()
        val latch = CountDownLatch(1)
        var capturedResult: Result<String>? = null

        section.aiServiceFactory = { _ ->
            FakeTestConnectionAIService(Result.failure(RuntimeException("401 Unauthorized")))
        }
        section.testConnectionResultHandler = { result ->
            capturedResult = result
            latch.countDown()
        }

        section.selectProvider(AiProvider.OPENAI)
        section.setBaseUrl("https://api.openai.com/v1")
        section.setModel("gpt-4o")

        section.triggerTestConnectionForTest()

        assertTrue("test connection should complete within timeout",
            latch.await(10, TimeUnit.SECONDS))
        assertTrue("result should be failure", capturedResult!!.isFailure)
        assertTrue("error should mention 401",
            capturedResult!!.exceptionOrNull()!!.message!!.contains("401"))
        assertTrue("button should be re-enabled", section.isTestConnectionButtonEnabled())
    }

    fun testTestConnectionButtonDisabledWhileRunning() {
        val section = AiAssistantSection()
        // A service that never completes — we only check the disabled state.
        val hangLatch = CountDownLatch(1)
        section.aiServiceFactory = { _ -> HangingAIService(hangLatch) }
        section.testConnectionResultHandler = { }

        section.selectProvider(AiProvider.OLLAMA)
        section.triggerTestConnectionForTest()

        // The button should be disabled + relabeled immediately after click.
        assertFalse("button should be disabled while testing",
            section.isTestConnectionButtonEnabled())
        assertEquals("Testing…", section.testConnectionButtonLabel())

        // Release the hang so the coroutine completes and doesn't leak.
        hangLatch.countDown()
    }

    /** Minimal fake AIService that returns a scripted testConnection result. */
    private class FakeTestConnectionAIService(private val result: Result<String>) : AIService {
        override suspend fun chat(request: AiChatRequest): AiChatResponse =
            error("not used in test-connection tests")
        override suspend fun testConnection(): Result<String> = result
    }

    /** A fake that blocks testConnection until a latch is released. */
    private class HangingAIService(private val latch: CountDownLatch) : AIService {
        override suspend fun chat(request: AiChatRequest): AiChatResponse = error("not used")
        override suspend fun testConnection(): Result<String> {
            latch.await()
            return Result.success("released")
        }
    }
}

