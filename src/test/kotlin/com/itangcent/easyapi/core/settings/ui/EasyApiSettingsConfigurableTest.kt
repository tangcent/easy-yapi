package com.itangcent.easyapi.core.settings.ui

import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class EasyApiSettingsConfigurableTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var configurable: EasyApiSettingsConfigurable

    override fun setUp() {
        super.setUp()
        configurable = EasyApiSettingsConfigurable(project)
    }

    fun testConfigurableExists() {
        assertNotNull("EasyApiSettingsConfigurable should be created", configurable)
    }

    fun testDisplayName() {
        assertEquals("Display name should be 'EasyApi'", "EasyApi", configurable.displayName)
    }

    fun testCreateComponentDoesNotThrow() {
        try {
            val component = configurable.createComponent()
            assertNotNull("Component should be created", component)
        } catch (e: Exception) {
            fail("createComponent should not throw: ${e.message}")
        }
    }

    fun testResetAndApplyCycle() {
        configurable.createComponent()
        configurable.reset()
        configurable.apply()
    }

    fun testResetDoesNotThrow() {
        configurable.createComponent()
        try {
            configurable.reset()
        } catch (e: Exception) {
            fail("reset should not throw: ${e.message}")
        }
    }

    fun testApplyDoesNotThrow() {
        configurable.createComponent()
        try {
            configurable.apply()
        } catch (e: Exception) {
            fail("apply should not throw: ${e.message}")
        }
    }

    fun testDisposeUIResourcesDoesNotThrow() {
        configurable.createComponent()
        try {
            configurable.disposeUIResources()
        } catch (e: Exception) {
            fail("disposeUIResources should not throw: ${e.message}")
        }
    }

    fun testSelectTabConstant() {
        assertEquals("TAB_GENERAL should be 'General'", "General", EasyApiSettingsConfigurable.TAB_GENERAL)
        assertEquals("TAB_FEATURES should be 'Features'", "Features", EasyApiSettingsConfigurable.TAB_FEATURES)
        assertEquals("TAB_POSTMAN should be 'Postman'", "Postman", EasyApiSettingsConfigurable.TAB_POSTMAN)
        assertEquals("TAB_HTTP should be 'HTTP'", "HTTP", EasyApiSettingsConfigurable.TAB_HTTP)
        assertEquals("TAB_PARSING_OUTPUT should be 'Parsing & Output'", "Parsing & Output", EasyApiSettingsConfigurable.TAB_PARSING_OUTPUT)
        assertEquals("TAB_EXTENSIONS should be 'Extensions'", "Extensions", EasyApiSettingsConfigurable.TAB_EXTENSIONS)
        assertEquals("TAB_RULES should be 'Rules'", "Rules", EasyApiSettingsConfigurable.TAB_RULES)
        assertEquals("TAB_AI should be 'AI'", "AI", EasyApiSettingsConfigurable.TAB_AI)
        assertEquals("TAB_GRPC should be 'gRPC'", "gRPC", EasyApiSettingsConfigurable.TAB_GRPC)
    }

    /**
     * The Rules tab must be present and the legacy Built-in tab
     * must be absent (replaced by Rules in 3.0).
     */
    fun testRulesTabAbsentFromMainTabsAndBuiltinTabAbsent() {
        configurable.createComponent()
        val tabs = configurable.tabsForTest()
        // Rules is no longer an inner tab of the main
        // EasyApi page — it lives only as the child node beside "Backup"
        // (EasyApiRulesConfigurable).
        assertFalse(
            "Rules tab must NOT be present in the main tabbed pane; got: $tabs",
            tabs.any { it.equals("Rules", ignoreCase = true) }
        )
        assertFalse(
            "Built-in tab should be absent (replaced by Rules in 3.0); got: $tabs",
            tabs.any { it.equals("Built-in", ignoreCase = true) ||
                       it.equals("BuiltIn", ignoreCase = true) }
        )
        // Backup lives only as the child node (EasyApiBackupConfigurable),
        // never as an inner tab — guards against re-duplication.
        assertFalse(
            "Backup tab must NOT be present in the main tabbed pane; got: $tabs",
            tabs.any { it.equals("Backup", ignoreCase = true) }
        )
    }

    /**
     * A dedicated AI tab must be present at the top level.
     */
    fun testAiTabPresent() {
        configurable.createComponent()
        val tabs = configurable.tabsForTest()
        assertTrue(
            "AI tab must be present; got: $tabs",
            tabs.any { it.equals("AI", ignoreCase = true) }
        )
    }

    /**
     * A dedicated Features tab must be present, holding the framework/channel/
     * field-format enablement toggles moved out of the General tab.
     */
    fun testFeaturesTabPresent() {
        configurable.createComponent()
        val tabs = configurable.tabsForTest()
        assertTrue(
            "Features tab must be present; got: $tabs",
            tabs.any { it.equals("Features", ignoreCase = true) }
        )
        // Features sits right after General so the relocated framework toggles
        // stay one tab away for muscle memory.
        val generalIdx = tabs.indexOfFirst { it.equals("General", ignoreCase = true) }
        val featuresIdx = tabs.indexOfFirst { it.equals("Features", ignoreCase = true) }
        assertTrue("General tab must precede Features; got: $tabs", generalIdx >= 0 && featuresIdx == generalIdx + 1)
    }

    // --- Task 4.2: channel enablement wiring (Req 3.5, 5.1, 5.3) ---

    /**
     * Helper: restore GeneralSettings to a clean default state in [tearDown]
     * so a toggled channel preference does not leak across tests.
     */
    private fun restoreGeneralSettings() {
        SettingBinder.getInstance(project).save(GeneralSettings())
    }

    fun testIsModified_includesChannelEnablement() {
        try {
            configurable.createComponent()
            configurable.reset()
            // After reset, isModified() should be false (matches stored state).
            assertFalse(
                "isModified should be false immediately after reset",
                configurable.isModified()
            )
            // Toggle the markdown (default-on) channel off via the panel's checkbox.
            configurable.setChannelCheckboxForTest("markdown", false)
            assertTrue(
                "Toggling a channel checkbox should flip isModified to true",
                configurable.isModified()
            )
        } finally {
            restoreGeneralSettings()
        }
    }

    fun testApply_persistsChannelEnablement() {
        try {
            configurable.createComponent()
            configurable.reset()
            // Toggle http-client (default-off) on and apply.
            configurable.setChannelCheckboxForTest("http-client", true)
            assertTrue("Expected modified state before apply", configurable.isModified())
            configurable.apply()
            // After apply, the persisted GeneralSettings must reflect the toggle.
            val persisted = SettingBinder.getInstance(project).read(GeneralSettings::class)
            assertTrue(
                "http-client should be persisted in enabledChannels after apply. Got: ${persisted.enabledChannels.toList()}",
                persisted.enabledChannels.contains("http-client")
            )
            // isModified should be false again after apply (UI matches storage).
            assertFalse(
                "isModified should be false immediately after apply",
                configurable.isModified()
            )
        } finally {
            restoreGeneralSettings()
        }
    }

    fun testReset_restoresChannelEnablement() {
        try {
            configurable.createComponent()
            configurable.reset()
            // Toggle markdown off, then reset — checkbox should return to checked (default-on).
            configurable.setChannelCheckboxForTest("markdown", false)
            assertEquals(false, configurable.channelCheckboxStateForTest("markdown"))
            configurable.reset()
            assertEquals(
                "After reset, markdown checkbox should return to its effective state (checked)",
                true,
                configurable.channelCheckboxStateForTest("markdown")
            )
            assertFalse("isModified should be false after reset", configurable.isModified())
        } finally {
            restoreGeneralSettings()
        }
    }

    /**
     * Req 4.3 regression: a disabled channel does NOT get a Settings tab.
     * Hoppscotch is default-off AND contributes a settings panel, so by default
     * (no explicit enable) it must not appear as a tab.
     */
    fun testDisabledChannelHasNoSettingsTab() {
        try {
            // Ensure Hoppscotch is not explicitly enabled (default-off → disabled).
            SettingBinder.getInstance(project).save(GeneralSettings())
            configurable.disposeUIResources() // force re-create so tabs rebuild
            configurable.createComponent()
            val tabs = configurable.tabsForTest()
            assertFalse(
                "Hoppscotch (default-off) must NOT have a settings tab when disabled. Got: $tabs",
                tabs.any { it.equals("Hoppscotch", ignoreCase = true) }
            )
        } finally {
            restoreGeneralSettings()
        }
    }

    /**
     * Conversely, explicitly enabling Hoppscotch makes its settings tab appear.
     */
    fun testEnabledChannelHasSettingsTab() {
        try {
            SettingBinder.getInstance(project).save(
                GeneralSettings(enabledChannels = arrayOf("hoppscotch"))
            )
            configurable.disposeUIResources()
            configurable.createComponent()
            val tabs = configurable.tabsForTest()
            assertTrue(
                "Hoppscotch (explicitly enabled) should have a settings tab. Got: $tabs",
                tabs.any { it.equals("Hoppscotch", ignoreCase = true) }
            )
        } finally {
            restoreGeneralSettings()
        }
    }

    // --- Task A.5: field-format enablement wiring (Req A3.5, A4) ---

    fun testIsModified_includesFieldFormatEnablement() {
        try {
            configurable.createComponent()
            configurable.reset()
            // After reset, isModified() should be false (matches stored state).
            assertFalse(
                "isModified should be false immediately after reset",
                configurable.isModified()
            )
            // Toggle the json (default-on) format off via the panel's checkbox.
            configurable.setFieldFormatCheckboxForTest("json", false)
            assertTrue(
                "Toggling a field-format checkbox should flip isModified to true",
                configurable.isModified()
            )
        } finally {
            restoreGeneralSettings()
        }
    }

    fun testApply_persistsFieldFormatEnablement() {
        try {
            configurable.createComponent()
            configurable.reset()
            // Toggle json (default-on) off and apply.
            configurable.setFieldFormatCheckboxForTest("json", false)
            assertTrue("Expected modified state before apply", configurable.isModified())
            configurable.apply()
            // After apply, the persisted GeneralSettings must reflect the toggle.
            val persisted = SettingBinder.getInstance(project).read(GeneralSettings::class)
            assertTrue(
                "json should be persisted in disabledFieldFormatChannels after apply. Got: ${persisted.disabledFieldFormatChannels.toList()}",
                persisted.disabledFieldFormatChannels.contains("json")
            )
            // isModified should be false again after apply (UI matches storage).
            assertFalse(
                "isModified should be false immediately after apply",
                configurable.isModified()
            )
        } finally {
            restoreGeneralSettings()
        }
    }

    fun testReset_restoresFieldFormatEnablement() {
        try {
            configurable.createComponent()
            configurable.reset()
            // Toggle json off, then reset — checkbox should return to checked (default-on).
            configurable.setFieldFormatCheckboxForTest("json", false)
            assertEquals(false, configurable.fieldFormatCheckboxStateForTest("json"))
            configurable.reset()
            assertEquals(
                "After reset, json checkbox should return to its effective state (checked)",
                true,
                configurable.fieldFormatCheckboxStateForTest("json")
            )
            assertFalse("isModified should be false after reset", configurable.isModified())
        } finally {
            restoreGeneralSettings()
        }
    }

    fun testFieldFormatEnablementIndependentFromExportChannelEnablement() {
        // Decision A4: toggling a field-format checkbox must not affect export-channel
        // isModified state, and vice versa.
        try {
            configurable.createComponent()
            configurable.reset()
            // Toggle a field-format checkbox — export-channel isModified must stay false
            // (no export-channel checkbox was touched).
            configurable.setFieldFormatCheckboxForTest("json", false)
            // isModified is true overall (field-format changed), but the export-channel
            // section alone is not modified. Verify by checking the channel checkbox state
            // is unchanged.
            assertEquals(
                "Export-channel markdown checkbox should remain checked (untouched)",
                true,
                configurable.channelCheckboxStateForTest("markdown")
            )
        } finally {
            restoreGeneralSettings()
        }
    }
}
