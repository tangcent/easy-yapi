package com.itangcent.easyapi.settings.ui

import com.itangcent.easyapi.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPanelsParityTest {

    @Test
    fun testExtensionPanelSerializationFlow() {
        val settings = Settings().apply {
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
        val settings = Settings().apply {
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
        val settings = Settings().apply {
            enumFieldAutoInferEnabled = true
        }
        val panel = IntelligentSettingsPanel()
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
        panel.applyTo(settings)
        assertEquals(true, settings.enumFieldAutoInferEnabled)
    }

    @Test
    fun testIntelligentPanelEnumFieldAutoInferEnabledChangeDetected() {
        val settings = Settings().apply {
            enumFieldAutoInferEnabled = false
        }
        val panel = IntelligentSettingsPanel()
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
        settings.enumFieldAutoInferEnabled = true
        assertTrue(panel.isModified(settings))
    }

    /**
     * Task 5.4: verify that the new Rules-tab fields round-trip through
     * `Settings`. The Rules tab UI itself uses `ToolbarDecorator` (which
     * requires the IntelliJ Application), so we test the data-model
     * round-trip here and rely on `ProjectRulesSubTabTest` /
     * `GlobalRulesSubTabTest` for the fixture-based UI round-trip.
     */
    @Test
    fun testRulesFieldsRoundTripThroughSettings() {
        val settings = Settings().apply {
            disabledAutoRuleFiles = arrayOf("/tmp/auto.rules")
            disabledGlobalRuleFiles = arrayOf("/tmp/global-disabled.rules")
        }
        val copy = settings.copy()
        assertEquals(settings.disabledAutoRuleFiles.toList(), copy.disabledAutoRuleFiles.toList())
        assertEquals(settings.disabledGlobalRuleFiles.toList(), copy.disabledGlobalRuleFiles.toList())
    }

    /**
     * Task 5.4: verify `Settings.equals`/`hashCode` include the new Rules
     * fields (silent parity-test failures occur otherwise).
     */
    @Test
    fun testSettingsEqualityIncludesRulesFields() {
        val base = Settings()
        assertFalse("disabledAutoRuleFiles should affect equality",
            base == base.copy(disabledAutoRuleFiles = arrayOf("/x")))
        assertFalse("disabledGlobalRuleFiles should affect equality",
            base == base.copy(disabledGlobalRuleFiles = arrayOf("/x")))
    }

    @Test
    fun testSettingsEqualityIncludesAiContextWindow() {
        val base = Settings()
        assertFalse("aiContextWindow should affect equality",
            base == base.copy(aiContextWindow = 200_000))
    }
}
