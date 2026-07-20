package com.itangcent.easyapi.channel.hoppscotch

import com.itangcent.easyapi.core.settings.state.UnifiedAppSettingsState
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class HoppscotchSettingsPanelPlatformTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var panel: HoppscotchSettingsPanel

    override fun setUp() {
        super.setUp()
        panel = HoppscotchSettingsPanel(project)
        // Reset Hoppscotch settings state to defaults so tests are independent
        // of PersistentStateComponent deserialization behavior.
        setHoppscotchField("hoppscotchToken", null)
        setHoppscotchField("hoppscotchServerUrl", "https://hoppscotch.io")
        setHoppscotchField("hoppscotchBackendUrl", null)
    }

    private fun setHoppscotchField(property: String, value: String?) {
        UnifiedAppSettingsState.getInstance().setValue("com.itangcent.easyapi.channel.hoppscotch.HoppscotchSettings", property, value)
    }

    private fun getHoppscotchField(property: String): String? {
        return UnifiedAppSettingsState.getInstance().getValue("com.itangcent.easyapi.channel.hoppscotch.HoppscotchSettings", property)
    }

    fun testResetFromDefaultSettings() {
        val settings = HoppscotchSettings()
        panel.resetFrom(settings)
        assertFalse("Panel should not be modified after reset with defaults", panel.isModified(settings))
    }

    fun testResetFromCustomSettings() {
        setHoppscotchField("hoppscotchToken", "my-token-12345678")
        setHoppscotchField("hoppscotchServerUrl", "https://custom.hoppscotch.io")
        setHoppscotchField("hoppscotchBackendUrl", "http://localhost:3170/v1")
        val settings = HoppscotchSettings()
        panel.resetFrom(settings)
        assertFalse("Panel should not be modified after reset with custom values", panel.isModified(settings))
    }

    fun testApplyToDefaultSettings() {
        val settings = HoppscotchSettings()
        panel.resetFrom(settings)

        val target = HoppscotchSettings()
        panel.applyTo(target)

        // Default server URL
        assertNotNull(getHoppscotchField("hoppscotchServerUrl"))
    }

    fun testApplyToCustomSettings() {
        setHoppscotchField("hoppscotchServerUrl", "https://custom.hoppscotch.io")
        setHoppscotchField("hoppscotchBackendUrl", "http://localhost:3170/v1")
        val settings = HoppscotchSettings()
        panel.resetFrom(settings)

        val target = HoppscotchSettings()
        panel.applyTo(target)

        assertEquals("https://custom.hoppscotch.io", getHoppscotchField("hoppscotchServerUrl"))
        assertEquals("http://localhost:3170/v1", getHoppscotchField("hoppscotchBackendUrl"))
    }

    fun testIsModifiedNullSettings() {
        assertFalse(panel.isModified(null))
    }

    fun testResetFromNullSettings() {
        panel.resetFrom(null)
        // Should not throw
    }

    fun testComponentNotNull() {
        assertNotNull(panel.component)
    }

    fun testResetFromWithToken() {
        setHoppscotchField("hoppscotchToken", "test-token-12345678")
        val settings = HoppscotchSettings()
        panel.resetFrom(settings)
        // Should not throw
    }

    fun testResetFromWithBlankServerUrl() {
        setHoppscotchField("hoppscotchServerUrl", "")
        val settings = HoppscotchSettings()
        panel.resetFrom(settings)
        // Should not throw
    }
}
