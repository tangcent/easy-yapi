package com.itangcent.easyapi.settings

import com.itangcent.easyapi.settings.module.GeneralSettings
import com.itangcent.easyapi.settings.module.HttpSettings
import com.itangcent.easyapi.settings.state.ApplicationSettingsState
import org.junit.Assert.*
import org.junit.Test

class SettingsDefaultsTest {

    @Test
    fun `test HttpSettings default httpTimeOut is 30 seconds`() {
        val settings = HttpSettings()
        assertEquals("Default httpTimeOut should be 30 seconds", 30, settings.httpTimeOut)
    }

    @Test
    fun `test ApplicationSettingsState State default httpTimeOut is 30 seconds`() {
        val state = ApplicationSettingsState.State()
        assertEquals(
            "ApplicationSettingsState default httpTimeOut should be 30 seconds",
            30,
            state.httpTimeOut
        )
    }

    @Test
    fun `test HttpSettings and ApplicationSettingsState have matching defaults`() {
        val settings = HttpSettings()
        val appState = ApplicationSettingsState.State()

        assertEquals(
            "httpTimeOut defaults should match between HttpSettings and ApplicationSettingsState",
            settings.httpTimeOut,
            appState.httpTimeOut
        )
    }

    @Test
    fun `test HttpSettings default unsafeSsl is false`() {
        val settings = HttpSettings()
        assertFalse(settings.unsafeSsl)
    }

    @Test
    fun `test HttpSettings default httpClient is APACHE`() {
        val settings = HttpSettings()
        assertEquals(HttpClientType.APACHE.value, settings.httpClient)
    }

    @Test
    fun `test GeneralSettings default gutterIconEnabled is true`() {
        val settings = GeneralSettings()
        assertTrue("gutterIconEnabled should default to true", settings.gutterIconEnabled)
    }

    @Test
    fun `test ApplicationSettingsState State default gutterIconEnabled is true`() {
        val state = ApplicationSettingsState.State()
        assertTrue(
            "ApplicationSettingsState default gutterIconEnabled should be true",
            state.gutterIconEnabled
        )
    }

    @Test
    fun `test GeneralSettings and ApplicationSettingsState have matching gutterIconEnabled defaults`() {
        val settings = GeneralSettings()
        val appState = ApplicationSettingsState.State()
        assertEquals(
            "gutterIconEnabled defaults should match between GeneralSettings and ApplicationSettingsState",
            settings.gutterIconEnabled,
            appState.gutterIconEnabled
        )
    }

    @Test
    fun `test GeneralSettings default enumFieldAutoInferEnabled is false`() {
        val settings = GeneralSettings()
        assertFalse(
            "Default enumFieldAutoInferEnabled should be false",
            settings.enumFieldAutoInferEnabled
        )
    }

    @Test
    fun `test ApplicationSettingsState State default enumFieldAutoInferEnabled is false`() {
        val state = ApplicationSettingsState.State()
        assertFalse(
            "ApplicationSettingsState default enumFieldAutoInferEnabled should be false",
            state.enumFieldAutoInferEnabled
        )
    }

    @Test
    fun `test GeneralSettings and ApplicationSettingsState have matching enumFieldAutoInferEnabled defaults`() {
        val settings = GeneralSettings()
        val appState = ApplicationSettingsState.State()
        assertEquals(
            "enumFieldAutoInferEnabled defaults should match between GeneralSettings and ApplicationSettingsState",
            settings.enumFieldAutoInferEnabled,
            appState.enumFieldAutoInferEnabled
        )
    }
}
