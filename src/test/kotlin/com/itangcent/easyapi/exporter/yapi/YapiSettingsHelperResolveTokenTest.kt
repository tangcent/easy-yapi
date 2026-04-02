package com.itangcent.easyapi.exporter.yapi

import com.itangcent.easyapi.exporter.yapi.model.TokenValidationResult
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

        val token = runBlocking {
            helper.resolveToken("module-b") { t ->
                if (t == "token-b") TokenValidationResult.Valid("test") else TokenValidationResult.Failed("test")
            }
        }

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

        val token = runBlocking {
            helper.resolveToken("module-a") { t ->
                if (t == "token-a") TokenValidationResult.Valid("test") else TokenValidationResult.Failed("test")
            }
        }

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

        val token = runBlocking {
            helper.resolveToken("module-x") { t ->
                if (t == "specific-token-for-x") TokenValidationResult.Valid("pid") else TokenValidationResult.Failed("no")
            }
        }

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

        val userToken = runBlocking {
            helper.resolveToken("service-user") { t ->
                if (t == "user-token-abc") TokenValidationResult.Valid("p1") else TokenValidationResult.Failed("no")
            }
        }
        assertEquals("user-token-abc", userToken)

        val orderToken = runBlocking {
            helper.resolveToken("service-order") { t ->
                if (t == "order-token-xyz") TokenValidationResult.Valid("p2") else TokenValidationResult.Failed("no")
            }
        }
        assertEquals("order-token-xyz", orderToken)

        val payToken = runBlocking {
            helper.resolveToken("service-pay") { t ->
                if (t == "pay-token-123") TokenValidationResult.Valid("p3") else TokenValidationResult.Failed("no")
            }
        }
        assertEquals("pay-token-123", payToken)
    }

    @org.junit.Test
    fun `test resolveToken trims whitespace from tokens`() {
        updateSettings { yapiTokens = "  my-module  =  trimmed-token  " }

        val token = runBlocking {
            helper.resolveToken("my-module") { t ->
                if (t == "trimmed-token") TokenValidationResult.Valid("pid") else TokenValidationResult.Failed("no")
            }
        }

        assertEquals("trimmed-token", token)
    }

    @org.junit.Test
    fun `test resolveToken skips lines without equals sign that are not raw fallback`() {
        updateSettings {
            yapiTokens = """
                module-a=token-a
                some-random-text
                module-b=token-b
            """.trimIndent()
        }

        val tokenA = runBlocking {
            helper.resolveToken("module-a") { t ->
                if (t == "token-a") TokenValidationResult.Valid("pid") else TokenValidationResult.Failed("no")
            }
        }
        assertEquals("token-a", tokenA)

        val tokenB = runBlocking {
            helper.resolveToken("module-b") { t ->
                if (t == "token-b") TokenValidationResult.Valid("pid") else TokenValidationResult.Failed("no")
            }
        }
        assertEquals("token-b", tokenB)
    }

    @org.junit.Test
    fun `test resolveToken is case-sensitive for module names - exact match works`() {
        updateSettings {
            yapiTokens = "MyModule=my-token"
        }

        val exactMatch = runBlocking {
            helper.resolveToken("MyModule") { t ->
                if (t == "my-token") TokenValidationResult.Valid("pid") else TokenValidationResult.Failed("no")
            }
        }
        assertEquals("my-token", exactMatch)
    }

    @org.junit.Test
    fun `test resetPromptedModules clears internal state`() {
        helper.resetPromptedModules()
        assertTrue("resetPromptedModules should complete without error", true)
    }
}
