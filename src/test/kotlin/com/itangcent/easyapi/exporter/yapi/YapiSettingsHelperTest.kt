package com.itangcent.easyapi.exporter.yapi

import com.itangcent.easyapi.exporter.yapi.model.TokenValidationResult
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.wrap
import kotlinx.coroutines.runBlocking

class YapiSettingsHelperTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var helper: DefaultYapiSettingsHelper

    override fun setUp() {
        super.setUp()
        val wrappedProject = wrap(project) {
            replaceService(SettingBinder::class, testSettingBinder)
        }
        helper = DefaultYapiSettingsHelper(wrappedProject)
    }

    @org.junit.Test
    fun `test resolveServerUrl returns normalized configured server`() {
        updateSettings {
            yapiServer = " http://localhost:3000/ "
        }

        val serverUrl = runBlocking { helper.resolveServerUrl() }

        assertEquals("http://localhost:3000", serverUrl)
    }

    @org.junit.Test
    fun `test resolveServerUrl in dumb mode returns null when server is missing`() {
        setSettings(createSettings())

        val serverUrl = runBlocking { helper.resolveServerUrl(dumb = true) }

        assertNull(serverUrl)
    }

    @org.junit.Test
    fun `test resolveToken returns module token from settings when validator accepts it`() {
        updateSettings {
            yapiTokens = """
                module-a=token-a
                module-b=token-b
            """.trimIndent()
        }

        val token = runBlocking {
            helper.resolveToken("module-b") { token ->
                if (token == "token-b") TokenValidationResult.Valid("test") else TokenValidationResult.Failed("test")
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
            helper.resolveToken("module-a") { token ->
                if (token == "token-a") TokenValidationResult.Valid("test") else TokenValidationResult.Failed("test")
            }
        }

        assertEquals("token-a", token)
    }
}
