package com.itangcent.easyapi.settings.ui

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
}
