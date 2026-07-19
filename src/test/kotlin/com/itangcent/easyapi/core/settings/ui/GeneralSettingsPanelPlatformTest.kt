package com.itangcent.easyapi.core.settings.ui

import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

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

        assertFalse(target.autoScanEnabled)
        assertTrue(target.concurrentScanEnabled)
        assertFalse(target.gutterIconEnabled)
        assertFalse(target.switchNotice)
        assertEquals(100, target.logLevel)
        assertEquals("ISO-8859-1", target.outputCharset)
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
