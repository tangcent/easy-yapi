package com.itangcent.easyapi.channel.hoppscotch

import org.junit.Assert.*
import org.junit.Test

class HoppscotchSettingsPanelLogicTest {

    // --- HoppscotchSettings Hoppscotch fields ---

    @Test
    fun testSettings_hoppscotchDefaults() {
        val state = HoppscotchSettings()
        assertNull(state.hoppscotchToken)
        assertEquals("https://hoppscotch.io", state.hoppscotchServerUrl)
        assertNull(state.hoppscotchBackendUrl)
        assertNull(state.hoppscotchRefreshToken)
    }

    @Test
    fun testSettings_hoppscotchCustomValues() {
        val state = HoppscotchSettings(
            hoppscotchToken = "my-token",
            hoppscotchServerUrl = "https://custom.hoppscotch.io",
            hoppscotchBackendUrl = "http://localhost:3170/v1",
            hoppscotchRefreshToken = "refresh-token"
        )
        assertEquals("my-token", state.hoppscotchToken)
        assertEquals("https://custom.hoppscotch.io", state.hoppscotchServerUrl)
        assertEquals("http://localhost:3170/v1", state.hoppscotchBackendUrl)
        assertEquals("refresh-token", state.hoppscotchRefreshToken)
    }

    @Test
    fun testSettings_hoppscotchEquality() {
        val s1 = HoppscotchSettings(
            hoppscotchToken = "token",
            hoppscotchServerUrl = "https://hoppscotch.io",
            hoppscotchBackendUrl = "http://localhost:3170"
        )
        val s2 = HoppscotchSettings(
            hoppscotchToken = "token",
            hoppscotchServerUrl = "https://hoppscotch.io",
            hoppscotchBackendUrl = "http://localhost:3170"
        )
        assertEquals(s1, s2)
        assertEquals(s1.hashCode(), s2.hashCode())
    }

    @Test
    fun testSettings_hoppscotchInequality() {
        val s1 = HoppscotchSettings(hoppscotchToken = "token1")
        val s2 = HoppscotchSettings(hoppscotchToken = "token2")
        assertNotEquals(s1, s2)
    }

    @Test
    fun testSettings_hoppscotchServerUrlInequality() {
        val s1 = HoppscotchSettings(hoppscotchServerUrl = "https://a.com")
        val s2 = HoppscotchSettings(hoppscotchServerUrl = "https://b.com")
        assertNotEquals(s1, s2)
    }

    @Test
    fun testSettings_hoppscotchBackendUrlInequality() {
        val s1 = HoppscotchSettings(hoppscotchBackendUrl = "http://a:3170")
        val s2 = HoppscotchSettings(hoppscotchBackendUrl = "http://b:3170")
        assertNotEquals(s1, s2)
    }

    @Test
    fun testSettings_hoppscotchRefreshTokenInequality() {
        val s1 = HoppscotchSettings(hoppscotchRefreshToken = "rt1")
        val s2 = HoppscotchSettings(hoppscotchRefreshToken = "rt2")
        assertNotEquals(s1, s2)
    }

    // --- HoppscotchSettingsPanel logic (without Project) ---
    // Note: HoppscotchSettingsPanel requires a Project for login operations.
    // We test the settings data flow and logic separately.

    @Test
    fun testSettings_hoppscotchCopy() {
        val s1 = HoppscotchSettings(hoppscotchToken = "token1", hoppscotchServerUrl = "https://a.com")
        val s2 = s1.copy(hoppscotchToken = "token2")
        assertEquals("token2", s2.hoppscotchToken)
        assertEquals("https://a.com", s2.hoppscotchServerUrl)
        assertEquals("token1", s1.hoppscotchToken) // original unchanged
    }

    @Test
    fun testSettings_hoppscotchNullToken() {
        val state = HoppscotchSettings(hoppscotchToken = null)
        assertNull(state.hoppscotchToken)
    }

    @Test
    fun testSettings_hoppscotchBlankServerUrl() {
        val state = HoppscotchSettings(hoppscotchServerUrl = "")
        assertEquals("", state.hoppscotchServerUrl)
    }

    @Test
    fun testSettings_hoppscotchNullBackendUrl() {
        val state = HoppscotchSettings(hoppscotchBackendUrl = null)
        assertNull(state.hoppscotchBackendUrl)
    }
}
