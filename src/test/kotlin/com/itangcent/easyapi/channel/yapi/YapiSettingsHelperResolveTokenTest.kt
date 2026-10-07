package com.itangcent.easyapi.channel.yapi

import com.intellij.openapi.ui.TestDialogManager
import com.itangcent.easyapi.channel.yapi.model.YapiResponse
import com.itangcent.easyapi.core.settings.state.UnifiedAppSettingsState
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*

class YapiSettingsHelperResolveTokenTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var helper: DefaultYapiSettingsHelper

    override fun setUp() {
        super.setUp()
        helper = DefaultYapiSettingsHelper(project)
        // Reset Yapi settings state to ensure test isolation (application-level service).
        UnifiedAppSettingsState.getInstance().getState().modules.remove("com.itangcent.easyapi.channel.yapi.YapiSettings")
    }

    private fun setYapiField(property: String, value: String?) {
        UnifiedAppSettingsState.getInstance().setValue("com.itangcent.easyapi.channel.yapi.YapiSettings", property, value)
    }

    /** Validator standing in for the server probe: accepts [accepted], rejects anything else. */
    private fun validator(vararg accepted: String): suspend (String) -> YapiResponse<*> = { token ->
        if (token in accepted) YapiResponse.success(Unit)
        else YapiResponse.failure<Unit>("token not accepted")
    }

    @org.junit.Test
    fun `test replacement prompt shows the reason the server gave`() {
        // This prompt is the only thing a user on the settings-token path sees before the export
        // fails, so the server's own reason has to be in it.
        setYapiField("yapiTokens", "module-a=token-a")
        val prompts = mutableListOf<String>()
        val previous = TestDialogManager.setTestInputDialog { prompt ->
            prompts.add(prompt)
            null
        }
        try {
            val token = runBlocking { helper.resolveToken("module-a", validator("other-token")) }
            assertNull("A rejected token must not be returned", token)
        } finally {
            TestDialogManager.setTestInputDialog(previous)
        }

        assertEquals(1, prompts.size)
        val prompt = prompts.single()
        assertTrue("Prompt should name the module: $prompt", prompt.contains("module-a"))
        assertTrue("Prompt should carry the server's reason: $prompt", prompt.contains("token not accepted"))
        assertTrue(prompt.contains("Please input a new Private Token"))
    }

    @org.junit.Test
    fun `test resolveToken returns module token from settings when validator accepts it`() {
        setYapiField("yapiTokens", """
            module-b=token-b
            module-a=token-a
        """.trimIndent())
        val token = runBlocking { helper.resolveToken("module-b", validator("token-b")) }
        assertEquals("token-b", token)
    }

    @org.junit.Test
    fun `test resolveToken ignores comments and blank token entries`() {
        setYapiField("yapiTokens", """
            # comment
            module-a=token-a
            invalid-line
            module-b=
        """.trimIndent())
        val token = runBlocking { helper.resolveToken("module-a", validator("token-a")) }
        assertEquals("token-a", token)
    }

    @org.junit.Test
    fun `test resolveToken prefers module-specific token over raw token`() {
        setYapiField("yapiTokens", """
            raw-global-token
            module-x=specific-token-for-x
        """.trimIndent())
        val token = runBlocking { helper.resolveToken("module-x", validator("specific-token-for-x")) }
        assertEquals("Should prefer module-specific token", "specific-token-for-x", token)
    }

    @org.junit.Test
    fun `test resolveToken handles multiple modules correctly`() {
        setYapiField("yapiTokens", """
            service-user=user-token-abc
            service-order=order-token-xyz
            service-pay=pay-token-123
        """.trimIndent())
        assertEquals("user-token-abc", runBlocking { helper.resolveToken("service-user", validator("user-token-abc")) })
        assertEquals("order-token-xyz", runBlocking { helper.resolveToken("service-order", validator("order-token-xyz")) })
        assertEquals("pay-token-123", runBlocking { helper.resolveToken("service-pay", validator("pay-token-123")) })
    }

    @org.junit.Test
    fun `test resolveToken trims whitespace from tokens`() {
        setYapiField("yapiTokens", "  my-module  =  trimmed-token  ")
        val token = runBlocking { helper.resolveToken("my-module", validator("trimmed-token")) }
        assertEquals("trimmed-token", token)
    }

    @org.junit.Test
    fun `test resolveToken skips lines without equals sign`() {
        setYapiField("yapiTokens", """
            module-a=token-a
            some-random-text
            module-b=token-b
        """.trimIndent())
        assertEquals("token-a", runBlocking { helper.resolveToken("module-a", validator("token-a")) })
        assertEquals("token-b", runBlocking { helper.resolveToken("module-b", validator("token-b")) })
    }

    @org.junit.Test
    fun `test resolveToken is case-sensitive for module names`() {
        setYapiField("yapiTokens", "MyModule=my-token")
        val token = runBlocking { helper.resolveToken("MyModule", validator("my-token")) }
        assertEquals("my-token", token)
    }

    @org.junit.Test
    fun `test resetPromptedModules clears internal state`() {
        helper.resetPromptedModules()
        assertTrue("resetPromptedModules should complete without error", true)
    }
}
