package com.itangcent.easyapi.settings.state

import com.itangcent.easyapi.settings.HttpClientType
import com.itangcent.easyapi.settings.MarkdownFormatType
import com.itangcent.easyapi.settings.PostmanJson5FormatType
import org.junit.Assert.*
import org.junit.Test

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
        assertEquals(50, s.logLevel)
        assertTrue(s.outputDemo)
        assertEquals("UTF-8", s.outputCharset)
        assertEquals(MarkdownFormatType.SIMPLE.name, s.markdownFormatType)
        assertNull(s.builtInConfig)
        assertArrayEquals(emptyArray(), s.remoteConfig)
        assertTrue(s.autoScanEnabled)
    }

    @Test
    fun testLoadState() {
        val state = ApplicationSettingsState()
        val newState = ApplicationSettingsState.State(
            feignEnable = true,
            jaxrsEnable = false,
            httpTimeOut = 30,
            logLevel = 100
        )
        state.loadState(newState)
        val loaded = state.state
        assertTrue(loaded.feignEnable)
        assertFalse(loaded.jaxrsEnable)
        assertEquals(30, loaded.httpTimeOut)
        assertEquals(100, loaded.logLevel)
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
}
