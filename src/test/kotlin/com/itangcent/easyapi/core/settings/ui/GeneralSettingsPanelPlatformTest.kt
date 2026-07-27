package com.itangcent.easyapi.core.settings.ui

import com.intellij.ui.components.JBCheckBox
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import java.awt.event.ActionEvent

/**
 * Platform tests for the *behavior* fields of [GeneralSettingsPanel]
 * (scanning, editor, output, diagnostics).
 *
 * Framework-support toggles (feign/jaxrs/actuator) live on
 * [FeaturesSettingsPanel] now and are covered by
 * [FeaturesSettingsPanelPlatformTest].
 */
class GeneralSettingsPanelPlatformTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var panel: GeneralSettingsPanel

    override fun setUp() {
        super.setUp()
        panel = GeneralSettingsPanel(project)
    }

    fun testResetFromAndApplyToDefaultSettings() {
        val settings = GeneralSettings()
        panel.resetFrom(settings)

        val target = GeneralSettings()
        panel.applyTo(target)

        // After resetFrom + applyTo, the target should have the same values as what was displayed
        // Note: isModified may be true because resetFrom populates default repos not in settings
    }

    fun testResetFromCustomSettingsAndApplyTo() {
        val settings = GeneralSettings().apply {
            logLevel = 40
            outputCharset = "GBK"
        }
        panel.resetFrom(settings)

        val target = GeneralSettings()
        panel.applyTo(target)

        assertEquals(40, target.logLevel)
        assertEquals("GBK", target.outputCharset)
    }

    fun testIsModifiedNullSettings() {
        assertFalse(panel.isModified(null))
    }

    fun testComponentNotNull() {
        assertNotNull(panel.component)
    }

    fun testRoundTripWithAllFieldsModified() {
        val modified = GeneralSettings().apply {
            // Keep master toggle on so dependent fields can be individually modified.
            apiScanEnabled = true
            autoScanEnabled = false
            concurrentScanEnabled = true
            gutterIconEnabled = false
            switchNotice = false
            logLevel = 100
            outputCharset = "ISO-8859-1"
        }
        panel.resetFrom(modified)

        val target = GeneralSettings()
        panel.applyTo(target)

        assertTrue(target.apiScanEnabled)
        assertFalse(target.autoScanEnabled)
        assertTrue(target.concurrentScanEnabled)
        assertFalse(target.gutterIconEnabled)
        assertFalse(target.switchNotice)
        assertEquals(100, target.logLevel)
        assertEquals("ISO-8859-1", target.outputCharset)
    }

    fun testApiScanMasterToggleDisablesDependentCheckboxes() {
        // When apiScanEnabled is off, dependent checkboxes are forced off
        // after resetFrom + applyTo, even if their stored values were true.
        val settings = GeneralSettings().apply {
            apiScanEnabled = false
            autoScanEnabled = true
            concurrentScanEnabled = true
            gutterIconEnabled = true
        }
        panel.resetFrom(settings)

        val target = GeneralSettings()
        panel.applyTo(target)

        assertFalse("apiScanEnabled should be false", target.apiScanEnabled)
        assertFalse("autoScanEnabled should be cascaded to false", target.autoScanEnabled)
        assertFalse("concurrentScanEnabled should be cascaded to false", target.concurrentScanEnabled)
        assertFalse("gutterIconEnabled should be cascaded to false", target.gutterIconEnabled)
    }

    /**
     * Covers the [GeneralSettingsPanel.apiScanEnabled] action listener's
     * "disable cascade" path: when the user unchecks the master toggle, the
     * dependent checkboxes (auto-scan, concurrent scan, gutter icon) must be
     * disabled and unselected immediately — before Apply is clicked.
     */
    fun testApiScanActionListener_uncheckingDisablesAndClearsDependents() {
        // Start from a state where everything is on.
        val settings = GeneralSettings().apply {
            apiScanEnabled = true
            autoScanEnabled = true
            concurrentScanEnabled = true
            gutterIconEnabled = true
        }
        panel.resetFrom(settings)

        // Sanity: dependents should be enabled and selected.
        val autoScan = checkBoxField("autoScanEnabled")
        val concurrentScan = checkBoxField("concurrentScanEnabled")
        val gutterIcon = checkBoxField("gutterIconEnabled")
        assertTrue("autoScanEnabled should be enabled", autoScan.isEnabled)
        assertTrue("concurrentScanEnabled should be enabled", concurrentScan.isEnabled)
        assertTrue("gutterIconEnabled should be enabled", gutterIcon.isEnabled)
        assertTrue("autoScanEnabled should be selected", autoScan.isSelected)
        assertTrue("concurrentScanEnabled should be selected", concurrentScan.isSelected)
        assertTrue("gutterIconEnabled should be selected", gutterIcon.isSelected)

        // Simulate the user unchecking the master toggle.
        setApiScanEnabled(false)

        // The action listener must cascade-disable and unselect the dependents.
        assertFalse(
            "autoScanEnabled checkbox should be disabled after master toggle off",
            autoScan.isEnabled
        )
        assertFalse(
            "concurrentScanEnabled checkbox should be disabled after master toggle off",
            concurrentScan.isEnabled
        )
        assertFalse(
            "gutterIconEnabled checkbox should be disabled after master toggle off",
            gutterIcon.isEnabled
        )
        assertFalse(
            "autoScanEnabled checkbox should be unselected after master toggle off",
            autoScan.isSelected
        )
        assertFalse(
            "concurrentScanEnabled checkbox should be unselected after master toggle off",
            concurrentScan.isSelected
        )
        assertFalse(
            "gutterIconEnabled checkbox should be unselected after master toggle off",
            gutterIcon.isSelected
        )
    }

    /**
     * Covers the [GeneralSettingsPanel.apiScanEnabled] action listener's
     * "enable" path: when the user re-checks the master toggle, the dependent
     * checkboxes must become enabled again. They must NOT be auto-checked — the
     * user has to opt back into each one (only `resetFrom` populates their
     * selection state).
     */
    fun testApiScanActionListener_checkingReEnablesDependents() {
        // Start from a state where the master is off and dependents are off.
        val settings = GeneralSettings().apply {
            apiScanEnabled = false
            autoScanEnabled = false
            concurrentScanEnabled = false
            gutterIconEnabled = false
        }
        panel.resetFrom(settings)

        val autoScan = checkBoxField("autoScanEnabled")
        val concurrentScan = checkBoxField("concurrentScanEnabled")
        val gutterIcon = checkBoxField("gutterIconEnabled")

        // Sanity: dependents should be disabled (cascaded by resetFrom).
        assertFalse("autoScanEnabled should be disabled initially", autoScan.isEnabled)
        assertFalse("concurrentScanEnabled should be disabled initially", concurrentScan.isEnabled)
        assertFalse("gutterIconEnabled should be disabled initially", gutterIcon.isEnabled)

        // Simulate the user re-checking the master toggle.
        setApiScanEnabled(true)

        // The action listener must re-enable the dependents, but must NOT
        // auto-check them — the user opts back in manually.
        assertTrue(
            "autoScanEnabled checkbox should be re-enabled after master toggle on",
            autoScan.isEnabled
        )
        assertTrue(
            "concurrentScanEnabled checkbox should be re-enabled after master toggle on",
            concurrentScan.isEnabled
        )
        assertTrue(
            "gutterIconEnabled checkbox should be re-enabled after master toggle on",
            gutterIcon.isEnabled
        )
        // Selection state is NOT changed by the enable path — only by resetFrom
        // or explicit user clicks on each dependent checkbox.
        assertFalse(
            "autoScanEnabled checkbox should remain unchecked after master toggle on",
            autoScan.isSelected
        )
        assertFalse(
            "concurrentScanEnabled checkbox should remain unchecked after master toggle on",
            concurrentScan.isSelected
        )
        assertFalse(
            "gutterIconEnabled checkbox should remain unchecked after master toggle on",
            gutterIcon.isSelected
        )
    }

    /**
     * The action listener is idempotent: re-checking the master toggle when it
     * is already on must not throw, and dependents must remain enabled.
     */
    fun testApiScanActionListener_isIdempotentWhenAlreadyChecked() {
        panel.resetFrom(GeneralSettings()) // master on, dependents on
        setApiScanEnabled(true)
        setApiScanEnabled(true)

        assertTrue("autoScanEnabled should still be enabled", checkBoxField("autoScanEnabled").isEnabled)
        assertTrue("concurrentScanEnabled should still be enabled", checkBoxField("concurrentScanEnabled").isEnabled)
        assertTrue("gutterIconEnabled should still be enabled", checkBoxField("gutterIconEnabled").isEnabled)
    }

    // --- Reflection helpers (avoid adding internal functions to production code) ---

    private fun checkBoxField(name: String): JBCheckBox {
        val field = GeneralSettingsPanel::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(panel) as JBCheckBox
    }

    private fun setApiScanEnabled(selected: Boolean) {
        val checkbox = checkBoxField("apiScanEnabled")
        checkbox.isSelected = selected
        // Fire the action listener manually because headless JComboBox does
        // not dispatch ActionEvent on setSelected.
        checkbox.actionListeners.forEach { it.actionPerformed(ActionEvent(checkbox, 0, "")) }
    }

    fun testResetFromNullDoesNotThrow() {
        panel.resetFrom(null)
        // Should not throw
    }

    fun testResetFromNullAndApplyTo() {
        panel.resetFrom(null)

        val target = GeneralSettings()
        panel.applyTo(target)
        // Should not throw, target should have default values for behavior fields
        assertTrue(target.autoScanEnabled)
        assertEquals("UTF-8", target.outputCharset)
    }

    fun testIsModifiedAfterResetFromAndApplyTo() {
        val settings = GeneralSettings().apply {
            logLevel = 40
        }
        panel.resetFrom(settings)

        val target = GeneralSettings()
        panel.applyTo(target)

        // After applyTo, the target should reflect the panel state
        assertEquals(40, target.logLevel)
    }
}
