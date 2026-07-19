package com.itangcent.easyapi.core.settings.ui

import com.itangcent.easyapi.channel.spi.Channel
import com.itangcent.easyapi.channel.spi.ChannelRegistry
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

/**
 * IDE-fixture tests for the "Export Channels" section of [FeaturesSettingsPanel]
 * (Task 4.1, Req 3.1–3.7).
 *
 * The test fixture loads `plugin.xml`, so [ChannelRegistry.allChannels] returns
 * the five production channels (markdown, postman, curl, http-client, hoppscotch).
 * Markdown/Postman/cURL are default-on; http-client/hoppscotch are default-off.
 */
class FeaturesSettingsPanelChannelEnablementTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var panel: FeaturesSettingsPanel

    override fun setUp() {
        super.setUp()
        // Constructing the panel builds the "Export Channels" section from
        // ChannelRegistry.allChannels() (unfiltered — Req 3.4).
        panel = FeaturesSettingsPanel(project)
    }

    private fun channels(): List<Channel> = ChannelRegistry.getInstance(project).allChannels()

    private fun defaultOn(): Channel = channels().first { it.id == "markdown" }
    private fun defaultOff(): Channel = channels().first { it.id == "http-client" }

    // --- Req 3.3: checkbox reflects effective enabled state on reset ---

    fun testResetChannelEnablement_defaultOnNoPreference_checked() {
        val settings = GeneralSettings() // no preference
        panel.resetChannelEnablementFrom(channels(), settings)
        assertTrue(
            "Default-on channel (markdown) with no preference should be checked",
            panel.channelCheckboxState("markdown") == true
        )
    }

    fun testResetChannelEnablement_defaultOffNoPreference_unchecked() {
        val settings = GeneralSettings()
        panel.resetChannelEnablementFrom(channels(), settings)
        assertEquals(
            "Default-off channel (http-client) with no preference should be unchecked",
            false,
            panel.channelCheckboxState("http-client")
        )
    }

    fun testResetChannelEnablement_explicitDisabled_unchecked() {
        // A default-on channel explicitly disabled renders unchecked.
        val settings = GeneralSettings(disabledChannels = arrayOf("markdown"))
        panel.resetChannelEnablementFrom(channels(), settings)
        assertEquals(
            "Default-on channel in disabledChannels should be unchecked",
            false,
            panel.channelCheckboxState("markdown")
        )
    }

    fun testResetChannelEnablement_explicitEnabled_checked() {
        // A default-off channel explicitly enabled renders checked.
        val settings = GeneralSettings(enabledChannels = arrayOf("http-client"))
        panel.resetChannelEnablementFrom(channels(), settings)
        assertEquals(
            "Default-off channel in enabledChannels should be checked",
            true,
            panel.channelCheckboxState("http-client")
        )
    }

    // --- applyChannelEnablementTo: writes correct arrays (design normalization) ---

    fun testApplyChannelEnablement_writesEnabledAndDisabledArrays() {
        // Start from defaults (all checkboxes reflect default state).
        panel.resetChannelEnablementFrom(channels(), GeneralSettings())

        // Toggle a default-off channel ON (http-client) and a default-on channel OFF (markdown).
        panel.setChannelCheckboxForTest("http-client", true)
        panel.setChannelCheckboxForTest("markdown", false)

        val target = GeneralSettings()
        panel.applyChannelEnablementTo(target)

        // http-client is default-off & checked → goes into enabledChannels.
        assertTrue(
            "http-client should be in enabledChannels. Got: ${target.enabledChannels.toList()}",
            target.enabledChannels.contains("http-client")
        )
        // markdown is default-on & unchecked → goes into disabledChannels.
        assertTrue(
            "markdown should be in disabledChannels. Got: ${target.disabledChannels.toList()}",
            target.disabledChannels.contains("markdown")
        )
        // http-client must NOT also be in disabledChannels (no redundant entry).
        assertFalse(
            "http-client should not be in disabledChannels",
            target.disabledChannels.contains("http-client")
        )
        // markdown must NOT also be in enabledChannels (explicit-on normalization does not apply here).
        assertFalse(
            "markdown should not be in enabledChannels",
            target.enabledChannels.contains("markdown")
        )
    }

    fun testApplyChannelEnablement_defaultOnChecked_isNotPersisted() {
        // A default-on channel that stays checked needs no entry (falls back to default-on).
        panel.resetChannelEnablementFrom(channels(), GeneralSettings())
        val target = GeneralSettings()
        panel.applyChannelEnablementTo(target)
        assertFalse(
            "markdown (default-on, checked) should produce no enabledChannels entry. Got: ${target.enabledChannels.toList()}",
            target.enabledChannels.contains("markdown")
        )
        assertFalse(
            "markdown (default-on, checked) should produce no disabledChannels entry. Got: ${target.disabledChannels.toList()}",
            target.disabledChannels.contains("markdown")
        )
    }

    fun testApplyChannelEnablement_explicitOnWinsWhenIdInBoth() {
        // Simulate a (corrupt) settings object where an id is in both arrays,
        // then apply the current (default) checkbox state. applyChannelEnablementTo
        // overwrites both arrays from the checkboxes, so the result must never
        // contain the same id in both (explicit-on wins normalization).
        panel.resetChannelEnablementFrom(channels(), GeneralSettings())
        // Toggle http-client on (default-off → enabled) and leave it as the only change.
        panel.setChannelCheckboxForTest("http-client", true)
        val target = GeneralSettings(
            enabledChannels = arrayOf("http-client"),
            disabledChannels = arrayOf("http-client")
        )
        panel.applyChannelEnablementTo(target)
        assertTrue(target.enabledChannels.contains("http-client"))
        assertFalse(
            "http-client must not be in disabledChannels after apply (explicit-on wins). Got: ${target.disabledChannels.toList()}",
            target.disabledChannels.contains("http-client")
        )
    }

    // --- isChannelEnablementModified ---

    fun testIsChannelEnablementModified_detectsChange() {
        val settings = GeneralSettings() // default state
        panel.resetChannelEnablementFrom(channels(), settings)
        // Now toggle a checkbox away from the effective state.
        panel.setChannelCheckboxForTest("markdown", false)
        assertTrue(
            "Toggling markdown off should mark channel enablement as modified",
            panel.isChannelEnablementModified(channels(), settings)
        )
    }

    fun testIsChannelEnablementModified_notModifiedWhenMatching() {
        val settings = GeneralSettings()
        panel.resetChannelEnablementFrom(channels(), settings)
        assertFalse(
            "After reset with matching settings, isChannelEnablementModified should be false",
            panel.isChannelEnablementModified(channels(), settings)
        )
    }

    fun testIsChannelEnablementModified_detectsDefaultOffToggledOn() {
        val settings = GeneralSettings() // http-client default-off → unchecked
        panel.resetChannelEnablementFrom(channels(), settings)
        panel.setChannelCheckboxForTest("http-client", true)
        assertTrue(
            "Toggling http-client on should mark channel enablement as modified",
            panel.isChannelEnablementModified(channels(), settings)
        )
    }

    // --- Req 3.7: graceful skip when no channels ---

    fun testPanelBuildsWithoutErrorWhenChannelsRegistered() {
        // The fixture registers channels, so allChannels() is non-empty.
        // Constructing the panel (done in setUp) must not throw, and the
        // checkboxes must be populated.
        assertTrue("Expected channels to be registered in fixture", channels().isNotEmpty())
        assertNotNull("markdown checkbox should exist", panel.channelCheckboxState("markdown"))
    }
}
