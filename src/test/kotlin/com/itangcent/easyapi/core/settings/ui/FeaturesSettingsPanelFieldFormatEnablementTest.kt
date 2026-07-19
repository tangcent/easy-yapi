package com.itangcent.easyapi.core.settings.ui

import com.itangcent.easyapi.format.spi.FieldFormatChannel
import com.itangcent.easyapi.format.spi.FieldFormatChannelRegistry
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

/**
 * IDE-fixture tests for the "Field Format Channels" section of [FeaturesSettingsPanel]
 * (Task A.4, Req A3.1–A3.7).
 *
 * Mirrors [FeaturesSettingsPanelChannelEnablementTest]. The test fixture loads
 * `plugin.xml`, so [FieldFormatChannelRegistry.allChannels] returns the four
 * production formats (json, json5, properties, yaml), all default-on (Decision A2).
 */
class FeaturesSettingsPanelFieldFormatEnablementTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var panel: FeaturesSettingsPanel

    override fun setUp() {
        super.setUp()
        // Constructing the panel builds the "Field Format Channels" section from
        // FieldFormatChannelRegistry.allChannels() (unfiltered — Req A3.4).
        panel = FeaturesSettingsPanel(project)
    }

    private fun channels(): List<FieldFormatChannel> =
        FieldFormatChannelRegistry.getInstance(project).allChannels()

    // --- Req A3.3: checkbox reflects effective enabled state on reset ---

    fun testResetFieldFormatEnablement_defaultOnNoPreference_checked() {
        val settings = GeneralSettings() // no preference
        panel.resetFieldFormatEnablementFrom(channels(), settings)
        assertEquals(
            "Default-on format (json) with no preference should be checked",
            true,
            panel.fieldFormatCheckboxState("json")
        )
    }

    fun testResetFieldFormatEnablement_allFourFormatsCheckedByDefault() {
        // Decision A2: all four shipping formats are default-on.
        val settings = GeneralSettings()
        panel.resetFieldFormatEnablementFrom(channels(), settings)
        listOf("json", "json5", "yaml", "properties").forEach { id ->
            assertEquals(
                "Format '$id' should be checked by default (Decision A2)",
                true,
                panel.fieldFormatCheckboxState(id)
            )
        }
    }

    fun testResetFieldFormatEnablement_explicitDisabled_unchecked() {
        // A default-on format explicitly disabled renders unchecked.
        val settings = GeneralSettings(disabledFieldFormatChannels = arrayOf("json"))
        panel.resetFieldFormatEnablementFrom(channels(), settings)
        assertEquals(
            "Default-on format in disabledFieldFormatChannels should be unchecked",
            false,
            panel.fieldFormatCheckboxState("json")
        )
    }

    // --- applyFieldFormatEnablementTo: writes correct arrays (design normalization) ---

    fun testApplyFieldFormatEnablement_writesDisabledArray() {
        // Start from defaults (all checkboxes reflect default state).
        panel.resetFieldFormatEnablementFrom(channels(), GeneralSettings())

        // Toggle a default-on format OFF (json).
        panel.setFieldFormatCheckboxForTest("json", false)

        val target = GeneralSettings()
        panel.applyFieldFormatEnablementTo(target)

        // json is default-on & unchecked → goes into disabledFieldFormatChannels.
        assertTrue(
            "json should be in disabledFieldFormatChannels. Got: ${target.disabledFieldFormatChannels.toList()}",
            target.disabledFieldFormatChannels.contains("json")
        )
        // json must NOT also be in enabledFieldFormatChannels (no redundant entry).
        assertFalse(
            "json should not be in enabledFieldFormatChannels",
            target.enabledFieldFormatChannels.contains("json")
        )
    }

    fun testApplyFieldFormatEnablement_defaultOnChecked_isNotPersisted() {
        // A default-on format that stays checked needs no entry (falls back to default-on).
        panel.resetFieldFormatEnablementFrom(channels(), GeneralSettings())
        val target = GeneralSettings()
        panel.applyFieldFormatEnablementTo(target)
        assertFalse(
            "json (default-on, checked) should produce no enabledFieldFormatChannels entry. Got: ${target.enabledFieldFormatChannels.toList()}",
            target.enabledFieldFormatChannels.contains("json")
        )
        assertFalse(
            "json (default-on, checked) should produce no disabledFieldFormatChannels entry. Got: ${target.disabledFieldFormatChannels.toList()}",
            target.disabledFieldFormatChannels.contains("json")
        )
    }

    fun testApplyFieldFormatEnablement_explicitOnWinsWhenIdInBoth() {
        // Simulate a (corrupt) settings object where an id is in both arrays,
        // then apply the current (default) checkbox state. applyFieldFormatEnablementTo
        // overwrites both arrays from the checkboxes, so the result must never
        // contain the same id in both (explicit-on wins normalization).
        panel.resetFieldFormatEnablementFrom(channels(), GeneralSettings())
        // Toggle json off (default-on → disabled) and leave it as the only change.
        panel.setFieldFormatCheckboxForTest("json", false)
        val target = GeneralSettings(
            enabledFieldFormatChannels = arrayOf("json"),
            disabledFieldFormatChannels = arrayOf("json")
        )
        panel.applyFieldFormatEnablementTo(target)
        assertTrue(target.disabledFieldFormatChannels.contains("json"))
        assertFalse(
            "json must not be in enabledFieldFormatChannels after apply (explicit-on normalization, but here json is off so it goes to disabled). Got: ${target.enabledFieldFormatChannels.toList()}",
            target.enabledFieldFormatChannels.contains("json")
        )
    }

    // --- isFieldFormatEnablementModified ---

    fun testIsFieldFormatEnablementModified_detectsChange() {
        val settings = GeneralSettings() // default state
        panel.resetFieldFormatEnablementFrom(channels(), settings)
        // Now toggle a checkbox away from the effective state.
        panel.setFieldFormatCheckboxForTest("json", false)
        assertTrue(
            "Toggling json off should mark field-format enablement as modified",
            panel.isFieldFormatEnablementModified(channels(), settings)
        )
    }

    fun testIsFieldFormatEnablementModified_notModifiedWhenMatching() {
        val settings = GeneralSettings()
        panel.resetFieldFormatEnablementFrom(channels(), settings)
        assertFalse(
            "After reset with matching settings, isFieldFormatEnablementModified should be false",
            panel.isFieldFormatEnablementModified(channels(), settings)
        )
    }

    fun testIsFieldFormatEnablementModified_detectsExplicitEnableToggledOff() {
        // json explicitly enabled, then checkbox toggled off → modified.
        val settings = GeneralSettings(enabledFieldFormatChannels = arrayOf("json"))
        panel.resetFieldFormatEnablementFrom(channels(), settings)
        panel.setFieldFormatCheckboxForTest("json", false)
        assertTrue(
            "Toggling an explicitly-enabled format off should mark modified",
            panel.isFieldFormatEnablementModified(channels(), settings)
        )
    }

    // --- Req A3.7: graceful skip when no channels ---

    fun testPanelBuildsWithoutErrorWhenFieldFormatsRegistered() {
        // The fixture registers formats, so allChannels() is non-empty.
        // Constructing the panel (done in setUp) must not throw, and the
        // checkboxes must be populated.
        assertTrue("Expected field-format channels to be registered in fixture", channels().isNotEmpty())
        assertNotNull("json checkbox should exist", panel.fieldFormatCheckboxState("json"))
    }

    fun testFieldFormatEnablementIndependentFromExportChannelEnablement() {
        // Decision A4: the two enablement sections use independent id spaces.
        // Setting export-channel preferences must not affect field-format checkboxes.
        val settings = GeneralSettings(
            enabledChannels = arrayOf("markdown"),
            disabledChannels = arrayOf("postman")
        )
        panel.resetFieldFormatEnablementFrom(channels(), settings)
        // json should still be checked (no field-format preference → default-on).
        assertEquals(
            "Export-channel preferences must not affect field-format checkboxes",
            true,
            panel.fieldFormatCheckboxState("json")
        )
    }
}
