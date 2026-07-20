package com.itangcent.easyapi.core.settings.state

import com.itangcent.easyapi.core.settings.HttpClientType
import com.itangcent.easyapi.core.settings.PostmanJson5FormatType
import org.junit.Assert.*
import org.junit.Test

@Suppress("DEPRECATION")  // exercises legacy State fields retained for one-time v4 migration
class ApplicationSettingsStateTest {

    @Test
    fun testDefaultState() {
        val state = ApplicationSettingsState()
        val s = state.state
        assertNotNull(s)
        assertFalse(s.feignEnable)
        assertTrue(s.jaxrsEnable)
        assertFalse(s.actuatorEnable)
        assertTrue(s.grpcEnable)
        assertNull(s.postmanToken)
        assertFalse(s.wrapCollection)
        assertFalse(s.autoMergeScript)
        assertEquals(PostmanJson5FormatType.EXAMPLE_ONLY.name, s.postmanJson5FormatType)
        assertTrue(s.queryExpanded)
        assertTrue(s.formExpanded)
        assertEquals("ALL", s.pathMulti)
        assertTrue(s.inferReturnMain)
        assertTrue(s.enableUrlTemplating)
        assertTrue(s.switchNotice)
        assertEquals(30, s.httpTimeOut)
        assertFalse(s.unsafeSsl)
        assertEquals(HttpClientType.APACHE.value, s.httpClient)
        assertEquals(100, s.logLevel) // SILENT — console off by default
        assertEquals("UTF-8", s.outputCharset)
        assertNull(s.builtInConfig)
        assertArrayEquals(emptyArray(), s.remoteConfig)
        assertTrue(s.autoScanEnabled)
        assertTrue(s.gutterIconEnabled)
    }

    @Test
    fun testLoadState() {
        val state = ApplicationSettingsState()
        val newState = ApplicationSettingsState.State(
            feignEnable = true,
            jaxrsEnable = false,
            httpTimeOut = 30,
            logLevel = 40
        )
        state.loadState(newState)
        val loaded = state.state
        assertTrue(loaded.feignEnable)
        assertFalse(loaded.jaxrsEnable)
        assertEquals(30, loaded.httpTimeOut)
        assertEquals(40, loaded.logLevel)
    }

    @Test
    fun testGetState_returnsSameInstance() {
        val state = ApplicationSettingsState()
        assertSame(state.state, state.state)
    }

    @Test
    fun testState_equality() {
        val s1 = ApplicationSettingsState.State()
        val s2 = ApplicationSettingsState.State()
        assertEquals(s1, s2)
        assertEquals(s1.hashCode(), s2.hashCode())
    }

    @Test
    fun testState_inequality() {
        val s1 = ApplicationSettingsState.State(feignEnable = true)
        val s2 = ApplicationSettingsState.State(feignEnable = false)
        assertNotEquals(s1, s2)
    }

    @Test
    fun testState_inequality_gutterIconEnabled() {
        val s1 = ApplicationSettingsState.State(gutterIconEnabled = true)
        val s2 = ApplicationSettingsState.State(gutterIconEnabled = false)
        assertNotEquals(s1, s2)
    }

    @Test
    fun testState_equalityWithArrays() {
        val s1 = ApplicationSettingsState.State(remoteConfig = arrayOf("http://a.com"))
        val s2 = ApplicationSettingsState.State(remoteConfig = arrayOf("http://a.com"))
        assertEquals(s1, s2)
    }

    @Test
    fun testState_inequalityWithArrays() {
        val s1 = ApplicationSettingsState.State(remoteConfig = arrayOf("http://a.com"))
        val s2 = ApplicationSettingsState.State(remoteConfig = arrayOf("http://b.com"))
        assertNotEquals(s1, s2)
    }

    @Test
    fun testState_copyTo() {
        val source = ApplicationSettingsState.State(
            feignEnable = true,
            httpTimeOut = 30,
            logLevel = 100,
            outputCharset = "ISO-8859-1"
        )
        val target = ApplicationSettingsState.State()
        source.copyTo(target)
        assertTrue(target.feignEnable)
        assertEquals(30, target.httpTimeOut)
        assertEquals(100, target.logLevel)
        assertEquals("ISO-8859-1", target.outputCharset)
    }

    @Test
    fun testState_copyTo_gutterIconEnabled() {
        val source = ApplicationSettingsState.State(gutterIconEnabled = false)
        val target = ApplicationSettingsState.State()
        assertTrue("target gutterIconEnabled should default to true", target.gutterIconEnabled)
        source.copyTo(target)
        assertFalse("gutterIconEnabled should be copied as false", target.gutterIconEnabled)
    }

    @Test
    fun testState_equalitySameInstance() {
        val s = ApplicationSettingsState.State()
        assertEquals(s, s)
    }

    @Test
    fun testState_equalityNull() {
        val s = ApplicationSettingsState.State()
        assertNotEquals(s, null)
    }

    @Test
    fun testState_equalityDifferentType() {
        val s = ApplicationSettingsState.State()
        assertNotEquals(s, "not a state")
    }

    @Test
    fun testState_inequality_enumFieldAutoInferEnabled() {
        val s1 = ApplicationSettingsState.State(enumFieldAutoInferEnabled = false)
        val s2 = ApplicationSettingsState.State(enumFieldAutoInferEnabled = true)
        assertNotEquals(s1, s2)
    }

    @Test
    fun testState_copyTo_enumFieldAutoInferEnabled() {
        val source = ApplicationSettingsState.State(enumFieldAutoInferEnabled = true)
        val target = ApplicationSettingsState.State()
        assertEquals(false, target.enumFieldAutoInferEnabled)
        source.copyTo(target)
        assertEquals(true, target.enumFieldAutoInferEnabled)
    }

    @Test
    fun testDefault_newGlobalRuleAndAiFields() {
        val s = ApplicationSettingsState.State()
        assertArrayEquals(emptyArray(), s.disabledGlobalRuleFiles)
        assertEquals("OPENAI", s.aiProvider)
        assertEquals("", s.aiBaseUrl)
        assertEquals("", s.aiModel)
        assertEquals(60, s.aiRequestTimeoutSec)
        assertEquals(100, s.aiMaxRequests)
        assertEquals(128_000, s.aiContextWindow)
    }

    @Test
    fun testState_equality_newAiScalarFields() {
        val s1 = ApplicationSettingsState.State(aiProvider = "GEMINI", aiBaseUrl = "u", aiModel = "m", aiRequestTimeoutSec = 90, aiMaxRequests = 50)
        val s2 = ApplicationSettingsState.State()
        assertNotEquals(s1, s2)
    }

    @Test
    fun testState_equality_aiContextWindow() {
        val s1 = ApplicationSettingsState.State(aiContextWindow = 200_000)
        val s2 = ApplicationSettingsState.State()
        assertNotEquals(s1, s2)
    }

    @Test
    fun testState_equality_newGlobalRuleArrays() {
        val s1 = ApplicationSettingsState.State(disabledGlobalRuleFiles = arrayOf("/x"))
        val s2 = ApplicationSettingsState.State(disabledGlobalRuleFiles = arrayOf("/x"))
        assertEquals(s1, s2)
        val s3 = ApplicationSettingsState.State(disabledGlobalRuleFiles = arrayOf("/z"))
        assertNotEquals(s1, s3)
    }

    @Test
    fun testState_copyTo_newAiAndGlobalRuleFields() {
        val source = ApplicationSettingsState.State(
            disabledGlobalRuleFiles = arrayOf("/gd"),
            aiProvider = "ANTHROPIC",
            aiBaseUrl = "https://api.anthropic.com",
            aiModel = "claude-3",
            aiRequestTimeoutSec = 75,
            aiMaxRequests = 25,
            aiContextWindow = 200_000
        )
        val target = ApplicationSettingsState.State()
        source.copyTo(target)
        assertArrayEquals(arrayOf("/gd"), target.disabledGlobalRuleFiles)
        assertEquals("ANTHROPIC", target.aiProvider)
        assertEquals("https://api.anthropic.com", target.aiBaseUrl)
        assertEquals("claude-3", target.aiModel)
        assertEquals(75, target.aiRequestTimeoutSec)
        assertEquals(25, target.aiMaxRequests)
        assertEquals(200_000, target.aiContextWindow)
    }
}
