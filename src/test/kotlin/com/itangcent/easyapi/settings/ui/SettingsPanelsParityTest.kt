package com.itangcent.easyapi.settings.ui

import com.itangcent.easyapi.settings.module.AiSettings
import com.itangcent.easyapi.settings.module.EnvironmentSettings
import com.itangcent.easyapi.settings.module.GeneralSettings
import com.itangcent.easyapi.settings.module.RuleFileSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPanelsParityTest {

    @Test
    fun testExtensionPanelSerializationFlow() {
        val settings = RuleFileSettings().apply {
            extensionConfigs = "jackson,gson"
        }
        val panel = ExtensionConfigPanel()
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
        panel.applyTo(settings)
        assertTrue(!settings.extensionConfigs.isNullOrBlank())
    }

    @Test
    fun testRemotePanelSerializationFlow() {
        val settings = RuleFileSettings().apply {
            remoteConfig = arrayOf("https://a.example/config", "!https://b.example/config")
        }
        val panel = RemoteConfigPanel()
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
        panel.applyTo(settings)
        assertTrue(settings.remoteConfig.isNotEmpty())
    }

    // Rules tab round-trip is covered by GlobalRulesSubTabTest and
    // ProjectRulesSubTabTest (fixture-based, since ToolbarDecorator requires
    // the IntelliJ Application).

    @Test
    fun testIntelligentPanelEnumFieldAutoInferEnabledRoundTrip() {
        val settings = GeneralSettings().apply {
            enumFieldAutoInferEnabled = true
        }
        val panel = IntelligentSettingsPanel()
        panel.resetEnumFieldFrom(settings)
        assertFalse(panel.isEnumFieldModified(settings))
        panel.applyEnumFieldTo(settings)
        assertEquals(true, settings.enumFieldAutoInferEnabled)
    }

    @Test
    fun testIntelligentPanelEnumFieldAutoInferEnabledChangeDetected() {
        val settings = GeneralSettings().apply {
            enumFieldAutoInferEnabled = false
        }
        val panel = IntelligentSettingsPanel()
        panel.resetEnumFieldFrom(settings)
        assertFalse(panel.isEnumFieldModified(settings))
        settings.enumFieldAutoInferEnabled = true
        assertTrue(panel.isEnumFieldModified(settings))
    }

    /**
     * Verify that the new Rules-tab fields round-trip through
     * the module data classes. The Rules tab UI itself uses `ToolbarDecorator`
     * (which requires the IntelliJ Application), so we test the data-model
     * round-trip here and rely on `ProjectRulesSubTabTest` /
     * `GlobalRulesSubTabTest` for the fixture-based UI round-trip.
     */
    @Test
    fun testRulesFieldsRoundTripThroughSettings() {
        val ruleFileSettings = RuleFileSettings().apply {
            disabledGlobalRuleFiles = arrayOf("/tmp/global-disabled.rules")
        }
        val envSettings = EnvironmentSettings().apply {
            disabledAutoRuleFiles = arrayOf("/tmp/auto.rules")
        }
        val ruleFileCopy = ruleFileSettings.copy()
        val envCopy = envSettings.copy()
        assertEquals(ruleFileSettings.disabledGlobalRuleFiles.toList(), ruleFileCopy.disabledGlobalRuleFiles.toList())
        assertEquals(envSettings.disabledAutoRuleFiles.toList(), envCopy.disabledAutoRuleFiles.toList())
    }

    /**
     * Verify module data class `equals`/`hashCode` include the
     * Rules fields (silent parity-test failures occur otherwise).
     */
    @Test
    fun testSettingsEqualityIncludesRulesFields() {
        val ruleBase = RuleFileSettings()
        assertFalse("disabledGlobalRuleFiles should affect equality",
            ruleBase == ruleBase.copy(disabledGlobalRuleFiles = arrayOf("/x")))

        val envBase = EnvironmentSettings()
        assertFalse("disabledAutoRuleFiles should affect equality",
            envBase == envBase.copy(disabledAutoRuleFiles = arrayOf("/x")))
    }

    @Test
    fun testSettingsEqualityIncludesAiContextWindow() {
        val base = AiSettings()
        assertFalse("aiContextWindow should affect equality",
            base == base.copy(aiContextWindow = 200_000))
    }
}
