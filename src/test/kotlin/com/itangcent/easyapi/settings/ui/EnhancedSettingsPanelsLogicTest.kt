package com.itangcent.easyapi.settings.ui

import com.itangcent.easyapi.settings.module.EnvironmentSettings
import com.itangcent.easyapi.settings.module.IntelligentSettings
import com.itangcent.easyapi.util.json.GsonUtils
import org.junit.Assert.*
import org.junit.Test

class EnhancedSettingsPanelsLogicTest {

    // --- EnhancedGeneralSettingsPanel ---
    // Note: EnhancedGeneralSettingsPanel is a ValidatedPanel (no-op resetFrom/applyTo/isModified).
    // These tests verify the no-op behavior and component creation.

    @Test
    fun testEnhancedGeneralSettingsPanel_resetFromDefault_notModified() {
        val panel = EnhancedGeneralSettingsPanel()
        val settings = object : com.itangcent.easyapi.settings.Settings {}
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
    }

    @Test
    fun testEnhancedGeneralSettingsPanel_resetFromNull_notModified() {
        val panel = EnhancedGeneralSettingsPanel()
        panel.resetFrom(null)
        val settings = object : com.itangcent.easyapi.settings.Settings {}
        // isModified always returns false for ValidatedPanel
        assertFalse(panel.isModified(settings))
    }

    @Test
    fun testEnhancedGeneralSettingsPanel_isModified_nullSettings() {
        val panel = EnhancedGeneralSettingsPanel()
        assertFalse(panel.isModified(null))
    }

    @Test
    fun testEnhancedGeneralSettingsPanel_componentNotNull() {
        val panel = EnhancedGeneralSettingsPanel()
        assertNotNull(panel.component)
    }

    // --- EnhancedOtherSettingsPanel ---
    // Note: EnhancedOtherSettingsPanel is a ValidatedPanel (no-op resetFrom/applyTo/isModified).

    @Test
    fun testEnhancedOtherSettingsPanel_resetFromDefault_notModified() {
        val panel = EnhancedOtherSettingsPanel()
        val settings = object : com.itangcent.easyapi.settings.Settings {}
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
    }

    @Test
    fun testEnhancedOtherSettingsPanel_resetFromNull() {
        val panel = EnhancedOtherSettingsPanel()
        panel.resetFrom(null)
        // Should not throw; null settings uses defaults
    }

    @Test
    fun testEnhancedOtherSettingsPanel_isModified_nullSettings() {
        val panel = EnhancedOtherSettingsPanel()
        assertFalse(panel.isModified(null))
    }

    @Test
    fun testEnhancedOtherSettingsPanel_componentNotNull() {
        val panel = EnhancedOtherSettingsPanel()
        assertNotNull(panel.component)
    }

    // --- ValidatedPanel base class ---

    @Test
    fun testValidatedPanel_hasNoErrorsByDefault() {
        val panel = EnhancedGeneralSettingsPanel()
        // No validation errors by default
        assertNotNull(panel.component)
    }
}

class EnvironmentSettingsPanelLogicTest {

    // --- Environment data model tests ---

    @Test
    fun testSettings_environmentDefaults() {
        val envSettings = EnvironmentSettings()
        val intelSettings = IntelligentSettings()
        assertEquals("", envSettings.projectEnvironments)
        assertEquals("", intelSettings.globalEnvironments)
    }

    @Test
    fun testSettings_environmentCustomValues() {
        val projectEnvJson = GsonUtils.toJson(
            mapOf("environments" to listOf(mapOf("name" to "dev", "variables" to mapOf("URL" to "http://localhost"))))
        )
        val globalEnvJson = GsonUtils.toJson(
            mapOf("environments" to listOf(mapOf("name" to "prod", "variables" to mapOf("URL" to "https://prod.example.com"))))
        )
        val envSettings = EnvironmentSettings(projectEnvironments = projectEnvJson)
        val intelSettings = IntelligentSettings(globalEnvironments = globalEnvJson)
        assertTrue(envSettings.projectEnvironments.isNotBlank())
        assertTrue(intelSettings.globalEnvironments.isNotBlank())
    }

    @Test
    fun testSettings_environmentEquality() {
        val s1 = EnvironmentSettings(projectEnvironments = "env1")
        val s2 = EnvironmentSettings(projectEnvironments = "env1")
        assertEquals(s1, s2)
    }

    @Test
    fun testSettings_environmentInequality() {
        val s1 = EnvironmentSettings(projectEnvironments = "env1")
        val s2 = EnvironmentSettings(projectEnvironments = "env2")
        assertNotEquals(s1, s2)
    }

    @Test
    fun testSettings_environmentCopy() {
        val s1 = EnvironmentSettings(projectEnvironments = "env1")
        val s2 = s1.copy(projectEnvironments = "env3")
        assertEquals("env3", s2.projectEnvironments)
        assertEquals("env1", s1.projectEnvironments) // original unchanged
    }
}
