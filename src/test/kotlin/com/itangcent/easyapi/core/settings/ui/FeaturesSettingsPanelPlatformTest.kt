package com.itangcent.easyapi.core.settings.ui

import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

/**
 * Platform tests for [FeaturesSettingsPanel] — the framework-support toggles
 * (feign/jaxrs/actuator) relocated from [GeneralSettingsPanelPlatformTest].
 *
 * Channel / field-format enablement on the same panel is covered by
 * [FeaturesSettingsPanelChannelEnablementTest] and
 * [FeaturesSettingsPanelFieldFormatEnablementTest].
 *
 * After the framework-enablement unification (Item 2), the panel's
 * `resetFrom`/`applyTo`/`isModified` are no-ops for framework toggles —
 * the dynamic UI is driven by `resetFrameworkEnablementFrom` /
 * `applyFrameworkEnablementTo` / `isFrameworkEnablementModified`, which
 * the `EasyApiSettingsConfigurable` invokes with the unfiltered recognizer
 * list. These tests pin the no-op contract: `applyTo` must not touch
 * `enabledFrameworks` / `disabledFrameworks`.
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

        // After resetFrom + applyTo, target remains at defaults (no-op for framework enablement).
        assertTrue(target.enabledFrameworks.isEmpty())
        assertTrue(target.disabledFrameworks.isEmpty())
    }

    fun testResetFromCustomSettingsAndApplyTo() {
        val settings = GeneralSettings().apply {
            enabledFrameworks = arrayOf("Feign", "SpringActuator")
            disabledFrameworks = arrayOf("JAX-RS")
        }
        panel.resetFrom(settings)

        val target = GeneralSettings()
        panel.applyTo(target)

        // applyTo is a no-op — target stays at defaults.
        assertTrue(target.enabledFrameworks.isEmpty())
        assertTrue(target.disabledFrameworks.isEmpty())
    }

    fun testIsModifiedNullSettings() {
        assertFalse(panel.isModified(null))
    }

    fun testComponentNotNull() {
        assertNotNull(panel.component)
    }

    fun testRoundTripWithAllFieldsModified() {
        val modified = GeneralSettings().apply {
            enabledFrameworks = arrayOf("Feign", "SpringActuator")
            disabledFrameworks = arrayOf("JAX-RS")
        }
        panel.resetFrom(modified)

        val target = GeneralSettings()
        panel.applyTo(target)

        // applyTo is a no-op — target stays at defaults.
        assertTrue(target.enabledFrameworks.isEmpty())
        assertTrue(target.disabledFrameworks.isEmpty())
    }

    fun testResetFromNullDoesNotThrow() {
        panel.resetFrom(null)
        // Should not throw
    }

    fun testResetFromNullAndApplyTo() {
        panel.resetFrom(null)

        val target = GeneralSettings()
        panel.applyTo(target)
        // Should not throw, target should remain at defaults.
        assertTrue(target.enabledFrameworks.isEmpty())
        assertTrue(target.disabledFrameworks.isEmpty())
    }

    fun testIsModifiedAfterResetFromAndApplyTo() {
        val settings = GeneralSettings().apply {
            enabledFrameworks = arrayOf("Feign")
        }
        panel.resetFrom(settings)

        val target = GeneralSettings()
        panel.applyTo(target)

        // applyTo is a no-op — target stays at defaults (no Feign entry).
        assertFalse(target.enabledFrameworks.contains("Feign"))
    }
}
