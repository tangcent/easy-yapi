package com.itangcent.easyapi.exporter.channel.yapi

import com.itangcent.easyapi.settings.state.UnifiedAppSettingsState
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*

class YapiSettingsHelperTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var helper: DefaultYapiSettingsHelper

    override fun setUp() {
        super.setUp()
        helper = DefaultYapiSettingsHelper(project)
        // Reset Yapi settings state to ensure test isolation (application-level service).
        UnifiedAppSettingsState.getInstance().getState().modules.remove("com.itangcent.easyapi.exporter.channel.yapi.YapiSettings")
    }

    private fun setYapiField(property: String, value: String?) {
        UnifiedAppSettingsState.getInstance().setValue("com.itangcent.easyapi.exporter.channel.yapi.YapiSettings", property, value)
    }

    @org.junit.Test
    fun `test resolveServerUrl returns normalized configured server`() {
        setYapiField("yapiServer", " http://localhost:3000/ ")
        val serverUrl = runBlocking { helper.resolveServerUrl() }
        assertEquals("http://localhost:3000", serverUrl)
    }

    @org.junit.Test
    fun `test resolveServerUrl in dumb mode returns null when server is missing`() {
        val serverUrl = runBlocking { helper.resolveServerUrl(dumb = true) }
        assertNull(serverUrl)
    }

    @org.junit.Test
    fun `test resolveToken returns module token from settings when validator accepts it`() {
        setYapiField("yapiTokens", """
            module-a=token-a
            module-b=token-b
        """.trimIndent())
        val token = runBlocking {
            helper.resolveToken("module-b") { it == "token-b" }
        }
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
        val token = runBlocking {
            helper.resolveToken("module-a") { it == "token-a" }
        }
        assertEquals("token-a", token)
    }
}
