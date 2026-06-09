package com.itangcent.easyapi.settings

import com.itangcent.easyapi.settings.state.ApplicationSettingsState
import org.junit.Assert.*
import org.junit.Test

class SettingsDefaultsTest {

    @Test
    fun `test Settings default httpTimeOut is 30 seconds`() {
        val settings = Settings()
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
    fun `test Settings and ApplicationSettingsState have matching defaults`() {
        val settings = Settings()
        val appState = ApplicationSettingsState.State()

        assertEquals(
            "httpTimeOut defaults should match between Settings and ApplicationSettingsState",
            settings.httpTimeOut,
            appState.httpTimeOut
        )
    }

    @Test
    fun `test Settings default unsafeSsl is false`() {
        val settings = Settings()
        assertFalse(settings.unsafeSsl)
    }

    @Test
    fun `test Settings default httpClient is APACHE`() {
        val settings = Settings()
        assertEquals(HttpClientType.APACHE.value, settings.httpClient)
    }

    @Test
    fun `test Settings default gutterIconEnabled is true`() {
        val settings = Settings()
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
    fun `test Settings and ApplicationSettingsState have matching gutterIconEnabled defaults`() {
        val settings = Settings()
        val appState = ApplicationSettingsState.State()
        assertEquals(
            "gutterIconEnabled defaults should match between Settings and ApplicationSettingsState",
            settings.gutterIconEnabled,
            appState.gutterIconEnabled
        )
    }

    @Test
    fun `test Settings and ApplicationSettingsState have matching yapi response wrapper defaults`() {
        val settings = Settings()
        val appState = ApplicationSettingsState.State()

        assertEquals(settings.yapiResponseWrapperEnabled, appState.yapiResponseWrapperEnabled)
        assertEquals(settings.yapiResponseWrapperTemplate, appState.yapiResponseWrapperTemplate)
    }
}
