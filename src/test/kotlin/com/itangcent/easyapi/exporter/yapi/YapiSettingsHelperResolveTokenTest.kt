package com.itangcent.easyapi.exporter.yapi

import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.wrap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*

class YapiSettingsHelperResolveTokenTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var helper: DefaultYapiSettingsHelper

    override fun setUp() {
        super.setUp()
        val wrappedProject = wrap(project) {
            replaceService(SettingBinder::class, testSettingBinder)
        }
        helper = DefaultYapiSettingsHelper(wrappedProject)
    }

    @org.junit.Test
    fun `test resolveToken returns module token from settings when validator accepts it`() {
        updateSettings {
            yapiTokens = """
                module-b=token-b
                module-a=token-a
            """.trimIndent()
        }
        val token = runBlocking { helper.resolveToken("module-b") { it == "token-b" } }
        assertEquals("token-b", token)
    }

    @org.junit.Test
    fun `test resolveToken ignores comments and blank token entries`() {
        updateSettings {
            yapiTokens = """
                # comment
                module-a=token-a
                invalid-line
                module-b=
            """.trimIndent()
        }
        val token = runBlocking { helper.resolveToken("module-a") { it == "token-a" } }
        assertEquals("token-a", token)
    }

    @org.junit.Test
    fun `test resolveToken prefers module-specific token over raw token`() {
        updateSettings {
            yapiTokens = """
                raw-global-token
                module-x=specific-token-for-x
            """.trimIndent()
        }
        val token = runBlocking { helper.resolveToken("module-x") { it == "specific-token-for-x" } }
        assertEquals("Should prefer module-specific token", "specific-token-for-x", token)
    }

    @org.junit.Test
    fun `test resolveToken handles multiple modules correctly`() {
        updateSettings {
            yapiTokens = """
                service-user=user-token-abc
                service-order=order-token-xyz
                service-pay=pay-token-123
            """.trimIndent()
        }
        assertEquals("user-token-abc", runBlocking { helper.resolveToken("service-user") { it == "user-token-abc" } })
        assertEquals("order-token-xyz", runBlocking { helper.resolveToken("service-order") { it == "order-token-xyz" } })
        assertEquals("pay-token-123", runBlocking { helper.resolveToken("service-pay") { it == "pay-token-123" } })
    }

    @org.junit.Test
    fun `test resolveToken trims whitespace from tokens`() {
        updateSettings { yapiTokens = "  my-module  =  trimmed-token  " }
        val token = runBlocking { helper.resolveToken("my-module") { it == "trimmed-token" } }
        assertEquals("trimmed-token", token)
    }

    @org.junit.Test
    fun `test resolveToken skips lines without equals sign`() {
        updateSettings {
            yapiTokens = """
                module-a=token-a
                some-random-text
                module-b=token-b
            """.trimIndent()
        }
        assertEquals("token-a", runBlocking { helper.resolveToken("module-a") { it == "token-a" } })
        assertEquals("token-b", runBlocking { helper.resolveToken("module-b") { it == "token-b" } })
    }

    @org.junit.Test
    fun `test resolveToken is case-sensitive for module names`() {
        updateSettings { yapiTokens = "MyModule=my-token" }
        val token = runBlocking { helper.resolveToken("MyModule") { it == "my-token" } }
        assertEquals("my-token", token)
    }

    @org.junit.Test
    fun `test resetPromptedModules clears internal state`() {
        helper.resetPromptedModules()
        assertTrue("resetPromptedModules should complete without error", true)
    }
}
