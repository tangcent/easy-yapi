package com.itangcent.easyapi.settings.ui

import com.itangcent.easyapi.settings.module.GeneralSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

/**
 * Platform tests for [FeaturesSettingsPanel] — the framework-support toggles
 * (feign/jaxrs/actuator) relocated from [GeneralSettingsPanelPlatformTest].
 *
 * Channel / field-format enablement on the same panel is covered by
 * [FeaturesSettingsPanelChannelEnablementTest] and
 * [FeaturesSettingsPanelFieldFormatEnablementTest].
 */
class FeaturesSettingsPanelPlatformTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var panel: FeaturesSettingsPanel

    override fun setUp() {
        super.setUp()
        panel = FeaturesSettingsPanel(project)
    }

    fun testResetFromAndApplyToDefaultSettings() {
        val settings = GeneralSettings()
        panel.resetFrom(settings)

        val target = GeneralSettings()
        panel.applyTo(target)

        // After resetFrom + applyTo, target reflects defaults.
        assertFalse(target.feignEnable)
        assertTrue(target.jaxrsEnable)
        assertFalse(target.actuatorEnable)
    }

    fun testResetFromCustomSettingsAndApplyTo() {
        val settings = GeneralSettings().apply {
            feignEnable = true
            jaxrsEnable = false
            actuatorEnable = true
        }
        panel.resetFrom(settings)

        val target = GeneralSettings()
        panel.applyTo(target)

        assertTrue(target.feignEnable)
        assertFalse(target.jaxrsEnable)
        assertTrue(target.actuatorEnable)
    }

    fun testIsModifiedNullSettings() {
        assertFalse(panel.isModified(null))
    }

    fun testComponentNotNull() {
        assertNotNull(panel.component)
    }

    fun testRoundTripWithAllFieldsModified() {
        val modified = GeneralSettings().apply {
            feignEnable = true
            jaxrsEnable = false
            actuatorEnable = true
        }
        panel.resetFrom(modified)

        val target = GeneralSettings()
        panel.applyTo(target)

        assertTrue(target.feignEnable)
        assertFalse(target.jaxrsEnable)
        assertTrue(target.actuatorEnable)
    }

    fun testResetFromNullDoesNotThrow() {
        panel.resetFrom(null)
        // Should not throw
    }

    fun testResetFromNullAndApplyTo() {
        panel.resetFrom(null)

        val target = GeneralSettings()
        panel.applyTo(target)
        // Should not throw, target should have default values for framework toggles.
        assertFalse(target.feignEnable)
        assertTrue(target.jaxrsEnable)
        assertFalse(target.actuatorEnable)
    }

    fun testIsModifiedAfterResetFromAndApplyTo() {
        val settings = GeneralSettings().apply {
            feignEnable = true
        }
        panel.resetFrom(settings)

        val target = GeneralSettings()
        panel.applyTo(target)

        // After applyTo, the target should reflect the panel state
        assertTrue(target.feignEnable)
    }
}
