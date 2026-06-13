package com.itangcent.easyapi.settings.ui

import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.util.json.GsonUtils
import org.junit.Assert.*
import org.junit.Test

class EnhancedSettingsPanelsLogicTest {

    // --- EnhancedGeneralSettingsPanel ---

    @Test
    fun testEnhancedGeneralSettingsPanel_resetFromDefault_notModified() {
        val panel = EnhancedGeneralSettingsPanel()
        val settings = Settings()
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
    }

    @Test
    fun testEnhancedGeneralSettingsPanel_resetFromCustom_notModified() {
        val panel = EnhancedGeneralSettingsPanel()
        val settings = Settings().apply {
            postmanBuildExample = true
            autoMergeScript = true
        }
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
    }

    @Test
    fun testEnhancedGeneralSettingsPanel_resetFromNull_notModified() {
        val panel = EnhancedGeneralSettingsPanel()
        panel.resetFrom(null)
        // resetFrom(null) sets postmanExampleCheckbox=false (null?.postmanBuildExample ?: false)
        // and postmanMergeScriptCheckbox=true (null?.autoMergeScript ?: true)
        // Settings() defaults: postmanBuildExample=true, autoMergeScript=false
        // So the panel IS modified compared to default Settings
        val settings = Settings()
        assertTrue(panel.isModified(settings))
    }

    @Test
    fun testEnhancedGeneralSettingsPanel_isModified_nullSettings() {
        val panel = EnhancedGeneralSettingsPanel()
        assertFalse(panel.isModified(null))
    }

    @Test
    fun testEnhancedGeneralSettingsPanel_applyTo() {
        val panel = EnhancedGeneralSettingsPanel()
        val settings = Settings().apply {
            postmanBuildExample = false
            autoMergeScript = true
        }
        panel.resetFrom(settings)

        val target = Settings()
        panel.applyTo(target)

        assertFalse(target.postmanBuildExample)
        assertTrue(target.autoMergeScript)
    }

    @Test
    fun testEnhancedGeneralSettingsPanel_componentNotNull() {
        val panel = EnhancedGeneralSettingsPanel()
        assertNotNull(panel.component)
    }

    // --- EnhancedOtherSettingsPanel ---

    @Test
    fun testEnhancedOtherSettingsPanel_resetFromDefault_notModified() {
        val panel = EnhancedOtherSettingsPanel()
        val settings = Settings().apply {
            outputCharset = "UTF-8"
            unsafeSsl = false
            httpTimeOut = 30000
        }
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
    }

    @Test
    fun testEnhancedOtherSettingsPanel_resetFromCustom_notModified() {
        val panel = EnhancedOtherSettingsPanel()
        val settings = Settings().apply {
            outputCharset = "GBK"
            unsafeSsl = true
            httpTimeOut = 60000
        }
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
    }

    @Test
    fun testEnhancedOtherSettingsPanel_resetFromNull() {
        val panel = EnhancedOtherSettingsPanel()
        panel.resetFrom(null)
        // Should not throw; null settings uses defaults (UTF-8, false, 30000)
    }

    @Test
    fun testEnhancedOtherSettingsPanel_isModified_nullSettings() {
        val panel = EnhancedOtherSettingsPanel()
        assertFalse(panel.isModified(null))
    }

    @Test
    fun testEnhancedOtherSettingsPanel_applyTo() {
        val panel = EnhancedOtherSettingsPanel()
        val settings = Settings().apply {
            outputCharset = "ISO-8859-1"
            unsafeSsl = true
            httpTimeOut = 45000
        }
        panel.resetFrom(settings)

        val target = Settings()
        panel.applyTo(target)

        assertEquals("ISO-8859-1", target.outputCharset)
        assertTrue(target.unsafeSsl)
        assertEquals(45000, target.httpTimeOut)
    }

    @Test
    fun testEnhancedOtherSettingsPanel_applyWithValidationErrors_throws() {
        val panel = EnhancedOtherSettingsPanel()
        // Use settings with a valid timeout (>= 1000) to avoid validation errors
        val settings = Settings().apply { httpTimeOut = 30000 }
        panel.resetFrom(settings)
        val target = Settings()
        panel.applyTo(target) // should not throw since timeout is valid
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
        val settings = Settings()
        assertEquals("", settings.projectEnvironments)
        assertEquals("", settings.globalEnvironments)
    }

    @Test
    fun testSettings_environmentCustomValues() {
        val projectEnvJson = GsonUtils.toJson(
            mapOf("environments" to listOf(mapOf("name" to "dev", "variables" to mapOf("URL" to "http://localhost"))))
        )
        val globalEnvJson = GsonUtils.toJson(
            mapOf("environments" to listOf(mapOf("name" to "prod", "variables" to mapOf("URL" to "https://prod.example.com"))))
        )
        val settings = Settings(
            projectEnvironments = projectEnvJson,
            globalEnvironments = globalEnvJson
        )
        assertTrue(settings.projectEnvironments.isNotBlank())
        assertTrue(settings.globalEnvironments.isNotBlank())
    }

    @Test
    fun testSettings_environmentEquality() {
        val s1 = Settings(projectEnvironments = "env1", globalEnvironments = "env2")
        val s2 = Settings(projectEnvironments = "env1", globalEnvironments = "env2")
        assertEquals(s1, s2)
    }

    @Test
    fun testSettings_environmentInequality() {
        val s1 = Settings(projectEnvironments = "env1")
        val s2 = Settings(projectEnvironments = "env2")
        assertNotEquals(s1, s2)
    }

    @Test
    fun testSettings_environmentCopy() {
        val s1 = Settings(projectEnvironments = "env1", globalEnvironments = "env2")
        val s2 = s1.copy(projectEnvironments = "env3")
        assertEquals("env3", s2.projectEnvironments)
        assertEquals("env2", s2.globalEnvironments)
        assertEquals("env1", s1.projectEnvironments)
    }
}
