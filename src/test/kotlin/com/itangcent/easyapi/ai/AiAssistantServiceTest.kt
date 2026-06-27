package com.itangcent.easyapi.ai

import com.itangcent.easyapi.ai.agent.AgentEvent
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

/**
 * Tests for [AiAssistantService].
 *
 * Verifies:
 * - [session] returns `null` when AI settings are not configured.
 * - [session] lazily builds a session when settings ARE configured.
 * - [resetConversation] clears the session (next [session] rebuilds).
 * - [AgentMemory] persists across [resetConversation] (the OLD session's
 * memory is discarded; a new session starts fresh).
 */
class AiAssistantServiceTest : EasyApiLightCodeInsightFixtureTestCase() {

    private val binder by lazy { SettingBinder.getInstance(project) }

    override fun tearDown() {
        // Restore default settings so tests stay isolated.
        runCatching {
            val s = binder.read()
            s.aiProvider = AiProvider.OPENAI.name
            s.aiBaseUrl = ""
            s.aiModel = ""
            binder.save(s)
        }
        super.tearDown()
    }

    /**
     * With default (empty) settings, [AiAssistantService.session] returns
     * `null` — no provider / base URL / model resolved.
     */
    fun testSessionIsNullWhenNotConfigured() {
        // Default settings: blank base URL + model → AiSettings.load returns null.
        val service = AiAssistantService.getInstance(project)
        assertNull("session should be null when AI is not configured", service.session())
        assertFalse("isConfigured should be false", service.isConfigured())
    }

    /**
     * When AI settings are configured (provider + base URL + model), and the
     * provider does not require an API key (e.g., OLLAMA), [session] builds
     * lazily and returns a non-null session.
     */
    fun testSessionLazilyBuiltWhenConfigured() {
        configureOllama()
        val service = AiAssistantService.getInstance(project)
        val sess = service.session()
        assertNotNull("session should be non-null when configured", sess)
        assertTrue("isConfigured should be true", service.isConfigured())
        // The session's memory starts empty.
        assertTrue("memory should start empty", sess!!.memory.messages.isEmpty())
    }

    /**
     * Calling [session] twice returns the SAME session instance (cached)
     * when settings haven't changed.
     */
    fun testSessionCachedWhenSettingsUnchanged() {
        configureOllama()
        val service = AiAssistantService.getInstance(project)
        val first = service.session()
        val second = service.session()
        assertSame("session should be cached", first, second)
    }

    /**
     * [resetConversation] clears the cached session; the next [session] call
     * builds a fresh one with empty memory.
     */
    fun testResetConversationClearsSession() {
        configureOllama()
        val service = AiAssistantService.getInstance(project)
        val first = service.session()!!
        // Mutate memory so we can detect a fresh session.
        first.memory.messages.add(
            com.itangcent.easyapi.ai.AiMessage.User("hello")
        )
        assertEquals(1, first.memory.messages.size)

        service.resetConversation()
        val second = service.session()
        assertNotSame("session should be rebuilt after reset", first, second)
        assertTrue("new session memory should be empty", second!!.memory.messages.isEmpty())
    }

    /**
     * [UiApprovalGate.complete] resumes a suspended [await] call.
     */
    fun testUiApprovalGateCompletesAwait() = runBlocking {
        val gate = UiApprovalGate()
        // Launch a consumer that awaits the gate.
        val result = async {
            gate.await("write_rule_file", emptyMap())
        }
        // Give the consumer a chance to suspend.
        yield()
        assertTrue("gate should report pending", gate.isPending())
        gate.complete(true)
        assertTrue("await should return true", result.await())
    }

    private fun configureOllama() {
        val s = binder.read()
        s.aiProvider = AiProvider.OLLAMA.name
        s.aiBaseUrl = AiProvider.OLLAMA.defaultBaseUrl!!
        s.aiModel = AiProvider.OLLAMA.defaultModel!!
        binder.save(s)
    }
}
