package com.itangcent.easyapi.channel.hoppscotch

import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.state.UnifiedAppSettingsState
import com.itangcent.easyapi.core.settings.update
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPasswordField
import javax.swing.JTextField

/**
 * Coverage-focused tests for [HoppscotchSettingsPanel].
 *
 * Fills gaps left by [com.itangcent.easyapi.core.settings.ui.HoppscotchSettingsPanelPlatformTest]:
 * - `applyTo` manual token branch (set when null, skip when already set, skip when blank)
 * - `applyTo` blank serverUrl/backendUrl → null conversion
 * - `isModified` for serverUrl, backendUrl, and manualToken fields
 * - `updateTokenStatus` "Logged in" / "Not logged in" branches (tokenStatusLabel + logoutButton)
 * - `resetFrom` with null serverUrl/backendUrl → default fallback
 * - Round-trip with all custom fields
 * - Multiple resetFrom calls
 *
 * Uses the IntelliJ Platform test framework ([EasyApiLightCodeInsightFixtureTestCase])
 * because [HoppscotchSettingsPanel] requires a [com.intellij.openapi.project.Project].
 */
class HoppscotchSettingsPanelCoverageTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var panel: HoppscotchSettingsPanel

    private val moduleKey = "com.itangcent.easyapi.channel.hoppscotch.HoppscotchSettings"

    override fun setUp() {
        super.setUp()
        panel = HoppscotchSettingsPanel(project)
        // Reset Hoppscotch settings to defaults so tests are independent.
        setSettings {
            hoppscotchToken = null
            hoppscotchServerUrl = "https://hoppscotch.io"
            hoppscotchBackendUrl = null
        }
    }

    // ---- Helpers ----

    private fun setSettings(updater: HoppscotchSettings.() -> Unit) {
        SettingBinder.getInstance(project).update(HoppscotchSettings::class, updater)
    }

    private fun getHoppscotchField(property: String): String? {
        return UnifiedAppSettingsState.getInstance().getValue(moduleKey, property)
    }

    private fun serverUrlField(): JTextField = getPrivateField("serverUrlField")
    private fun backendUrlField(): JTextField = getPrivateField("backendUrlField")
    private fun manualTokenField(): JPasswordField = getPrivateField("manualTokenField")
    private fun tokenStatusLabel(): JLabel = getPrivateField("tokenStatusLabel")
    private fun logoutButton(): JButton = getPrivateField("logoutButton")

    private fun <T> getPrivateField(name: String): T {
        val field = HoppscotchSettingsPanel::class.java.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(panel) as T
    }

    // ---- applyTo: manual token branch (lines 142-144) ----

    fun testApplyTo_manualToken_setsWhenSettingsTokenIsNull() {
        // Settings token is null (from setUp)
        panel.resetFrom(HoppscotchSettings())
        manualTokenField().text = "my-manual-token"

        panel.applyTo(HoppscotchSettings())

        assertEquals("my-manual-token", getHoppscotchField("hoppscotchToken"))
    }

    fun testApplyTo_manualToken_doesNotOverrideExistingToken() {
        setSettings { hoppscotchToken = "existing-token" }
        panel.resetFrom(HoppscotchSettings())
        manualTokenField().text = "manual-token-attempt"

        panel.applyTo(HoppscotchSettings())

        assertEquals("existing-token", getHoppscotchField("hoppscotchToken"))
    }

    fun testApplyTo_blankManualToken_doesNotSet() {
        // Settings token is null (from setUp)
        panel.resetFrom(HoppscotchSettings())
        manualTokenField().text = "   " // blank

        panel.applyTo(HoppscotchSettings())

        assertNull(getHoppscotchField("hoppscotchToken"))
    }

    // ---- applyTo: blank URL → null conversion ----

    fun testApplyTo_blankServerUrl_setsNull() {
        setSettings { hoppscotchServerUrl = "https://custom.example.com" }
        panel.resetFrom(HoppscotchSettings())
        serverUrlField().text = ""

        panel.applyTo(HoppscotchSettings())

        assertNull(getHoppscotchField("hoppscotchServerUrl"))
    }

    fun testApplyTo_blankBackendUrl_setsNull() {
        setSettings { hoppscotchBackendUrl = "http://localhost:3170/v1" }
        panel.resetFrom(HoppscotchSettings())
        backendUrlField().text = ""

        panel.applyTo(HoppscotchSettings())

        assertNull(getHoppscotchField("hoppscotchBackendUrl"))
    }

    // ---- isModified: serverUrl / backendUrl changes ----

    fun testIsModified_serverUrlChanged_returnsTrue() {
        setSettings { hoppscotchServerUrl = "https://original.example.com" }
        panel.resetFrom(HoppscotchSettings())

        // Change the UI field
        serverUrlField().text = "https://changed.example.com"

        assertTrue(panel.isModified(HoppscotchSettings()))
    }

    fun testIsModified_backendUrlChanged_returnsTrue() {
        setSettings { hoppscotchBackendUrl = "http://original:3170/v1" }
        panel.resetFrom(HoppscotchSettings())

        backendUrlField().text = "http://changed:8080/v1"

        assertTrue(panel.isModified(HoppscotchSettings()))
    }

    // ---- isModified: manual token branch (line 152) ----

    fun testIsModified_manualTokenEnteredAndSettingsTokenBlank_returnsTrue() {
        // Settings token is null (from setUp)
        panel.resetFrom(HoppscotchSettings())
        manualTokenField().text = "new-manual-token"

        assertTrue(panel.isModified(HoppscotchSettings()))
    }

    fun testIsModified_manualTokenEnteredAndSettingsTokenSet_returnsFalse() {
        setSettings { hoppscotchToken = "existing-token" }
        panel.resetFrom(HoppscotchSettings())
        manualTokenField().text = "different-token"

        assertFalse(panel.isModified(HoppscotchSettings()))
    }

    fun testIsModified_blankManualTokenAndSettingsTokenNull_returnsFalse() {
        // Settings token is null (from setUp)
        panel.resetFrom(HoppscotchSettings())
        manualTokenField().text = ""

        assertFalse(panel.isModified(HoppscotchSettings()))
    }

    // ---- updateTokenStatus: "Logged in" branch (lines 104-108) ----

    fun testResetFrom_withToken_showsLoggedInStatus() {
        setSettings { hoppscotchToken = "abcd1234efgh5678" }
        panel.resetFrom(HoppscotchSettings())

        val statusText = tokenStatusLabel().text
        assertTrue("Status should indicate logged in, was: $statusText", statusText.startsWith("Logged in"))
        assertTrue("Logout button should be enabled when token is set", logoutButton().isEnabled)
    }

    fun testResetFrom_withNullToken_showsNotLoggedInStatus() {
        // Settings token is null (from setUp)
        panel.resetFrom(HoppscotchSettings())

        assertEquals("Not logged in", tokenStatusLabel().text)
        assertFalse("Logout button should be disabled when token is null", logoutButton().isEnabled)
    }

    fun testResetFrom_withBlankToken_showsNotLoggedInStatus() {
        setSettings { hoppscotchToken = "   " }
        panel.resetFrom(HoppscotchSettings())

        assertEquals("Not logged in", tokenStatusLabel().text)
        assertFalse(logoutButton().isEnabled)
    }

    fun testResetWithToken_statusContainsTokenPrefix() {
        setSettings { hoppscotchToken = "tokenXYZ12345678" }
        panel.resetFrom(HoppscotchSettings())

        // The status label should show the first 8 chars of the token
        val statusText = tokenStatusLabel().text
        assertTrue("Status should contain token prefix, was: $statusText", statusText.contains("tokenXYZ"))
    }

    // ---- resetFrom: null serverUrl/backendUrl → default fallback (lines 131-133) ----

    fun testResetFrom_nullServerUrl_usesDefault() {
        setSettings { hoppscotchServerUrl = null }
        panel.resetFrom(HoppscotchSettings())

        assertEquals("https://hoppscotch.io", serverUrlField().text)
    }

    fun testResetFrom_nullBackendUrl_usesEmptyString() {
        setSettings { hoppscotchBackendUrl = null }
        panel.resetFrom(HoppscotchSettings())

        assertEquals("", backendUrlField().text)
    }

    fun testResetFrom_nullToken_usesEmptyString() {
        setSettings { hoppscotchToken = null }
        panel.resetFrom(HoppscotchSettings())

        assertEquals("", manualTokenField().text)
    }

    // ---- Round-trip with all custom fields ----

    fun testRoundTrip_allCustomFields() {
        setSettings {
            hoppscotchServerUrl = "https://custom.example.com"
            hoppscotchBackendUrl = "http://localhost:3170/v1"
            hoppscotchToken = "round-trip-token"
        }
        panel.resetFrom(HoppscotchSettings())
        assertFalse("Panel should not be modified after reset", panel.isModified(HoppscotchSettings()))

        panel.applyTo(HoppscotchSettings())

        assertEquals("https://custom.example.com", getHoppscotchField("hoppscotchServerUrl"))
        assertEquals("http://localhost:3170/v1", getHoppscotchField("hoppscotchBackendUrl"))
        assertEquals("round-trip-token", getHoppscotchField("hoppscotchToken"))
    }

    // ---- Multiple resetFrom calls ----

    fun testMultipleResetFrom_lastOneWins() {
        setSettings {
            hoppscotchServerUrl = "https://first.example.com"
            hoppscotchBackendUrl = "http://first:3170/v1"
        }
        panel.resetFrom(HoppscotchSettings())

        setSettings {
            hoppscotchServerUrl = "https://second.example.com"
            hoppscotchBackendUrl = "http://second:8080/v1"
        }
        panel.resetFrom(HoppscotchSettings())

        assertEquals("https://second.example.com", serverUrlField().text)
        assertEquals("http://second:8080/v1", backendUrlField().text)
        assertFalse(panel.isModified(HoppscotchSettings()))
    }

    // ---- applyTo then isModified consistency ----

    fun testApplyTo_thenIsModified_false() {
        setSettings {
            hoppscotchServerUrl = "https://pre-apply.example.com"
            hoppscotchBackendUrl = "http://pre:3170/v1"
        }
        panel.resetFrom(HoppscotchSettings())

        // Change UI fields
        serverUrlField().text = "https://post-apply.example.com"
        backendUrlField().text = "http://post:8080/v1"
        assertTrue(panel.isModified(HoppscotchSettings()))

        // Apply changes
        panel.applyTo(HoppscotchSettings())

        // After applyTo, settings should match UI → not modified
        assertFalse(panel.isModified(HoppscotchSettings()))
    }

    // ---- applyTo does not clear existing token when manual token is blank ----

    fun testApplyTo_blankManualToken_doesNotClearExistingToken() {
        setSettings { hoppscotchToken = "preserve-me" }
        panel.resetFrom(HoppscotchSettings())
        manualTokenField().text = ""

        panel.applyTo(HoppscotchSettings())

        assertEquals("preserve-me", getHoppscotchField("hoppscotchToken"))
    }
}
