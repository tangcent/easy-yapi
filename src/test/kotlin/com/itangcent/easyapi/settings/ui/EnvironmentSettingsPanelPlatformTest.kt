package com.itangcent.easyapi.settings.ui

import com.itangcent.easyapi.settings.module.EnvironmentSettings
import com.itangcent.easyapi.settings.module.IntelligentSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.util.json.GsonUtils

class EnvironmentSettingsPanelPlatformTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var panel: EnvironmentSettingsPanel

    override fun setUp() {
        super.setUp()
        panel = EnvironmentSettingsPanel(project)
    }

    fun testResetFromAndApplyToDefaultSettings() {
        val envSettings = EnvironmentSettings()
        val globalEnvSettings = IntelligentSettings()
        panel.resetFrom(envSettings)
        panel.resetGlobalEnvsFrom(globalEnvSettings)

        val target = EnvironmentSettings()
        val globalTarget = IntelligentSettings()
        panel.applyTo(target)
        panel.applyGlobalEnvsTo(globalTarget)

        assertEquals("", target.projectEnvironments)
        assertEquals("", globalTarget.globalEnvironments)
    }

    fun testResetFromWithProjectEnvironments() {
        val projectEnvJson = GsonUtils.toJson(
            mapOf("environments" to listOf(
                mapOf("name" to "dev", "scope" to "PROJECT", "variables" to mapOf("URL" to "http://localhost:8080"))
            ))
        )
        val settings = EnvironmentSettings().apply {
            this.projectEnvironments = projectEnvJson
        }
        panel.resetFrom(settings)

        val target = EnvironmentSettings()
        panel.applyTo(target)

        assertTrue(target.projectEnvironments.isNotBlank())
    }

    fun testResetFromWithGlobalEnvironments() {
        val globalEnvJson = GsonUtils.toJson(
            mapOf("environments" to listOf(
                mapOf("name" to "prod", "scope" to "GLOBAL", "variables" to mapOf("URL" to "https://prod.example.com"))
            ))
        )
        val globalEnvSettings = IntelligentSettings().apply {
            this.globalEnvironments = globalEnvJson
        }
        panel.resetFrom(EnvironmentSettings())
        panel.resetGlobalEnvsFrom(globalEnvSettings)

        val target = IntelligentSettings()
        panel.applyGlobalEnvsTo(target)

        assertTrue(target.globalEnvironments.isNotBlank())
    }

    fun testIsModifiedNullSettings() {
        assertFalse(panel.isModified(null))
    }

    fun testComponentNotNull() {
        assertNotNull(panel.component)
    }

    fun testResetFromEmptyEnvironments() {
        val envSettings = EnvironmentSettings().apply {
            projectEnvironments = ""
        }
        val globalEnvSettings = IntelligentSettings().apply {
            globalEnvironments = ""
        }
        panel.resetFrom(envSettings)
        panel.resetGlobalEnvsFrom(globalEnvSettings)

        val target = EnvironmentSettings()
        val globalTarget = IntelligentSettings()
        panel.applyTo(target)
        panel.applyGlobalEnvsTo(globalTarget)

        assertEquals("", target.projectEnvironments)
        assertEquals("", globalTarget.globalEnvironments)
    }

    fun testResetFromMalformedJson() {
        val envSettings = EnvironmentSettings().apply {
            projectEnvironments = "not-valid-json"
        }
        val globalEnvSettings = IntelligentSettings().apply {
            globalEnvironments = "also-not-valid"
        }
        panel.resetFrom(envSettings)
        panel.resetGlobalEnvsFrom(globalEnvSettings)
        // Should not throw, just ignore malformed JSON

        val target = EnvironmentSettings()
        val globalTarget = IntelligentSettings()
        panel.applyTo(target)
        panel.applyGlobalEnvsTo(globalTarget)
        // Should produce empty environments
    }

    fun testResetFromNullDoesNotThrow() {
        panel.resetFrom(null)
        // Should not throw
    }

    fun testResetFromWithBothEnvironments() {
        val projectEnvJson = GsonUtils.toJson(
            mapOf("environments" to listOf(
                mapOf("name" to "dev", "scope" to "PROJECT", "variables" to mapOf("URL" to "http://localhost"))
            ))
        )
        val globalEnvJson = GsonUtils.toJson(
            mapOf("environments" to listOf(
                mapOf("name" to "prod", "scope" to "GLOBAL", "variables" to mapOf("URL" to "https://prod.example.com"))
            ))
        )
        val envSettings = EnvironmentSettings().apply {
            this.projectEnvironments = projectEnvJson
        }
        val globalEnvSettings = IntelligentSettings().apply {
            this.globalEnvironments = globalEnvJson
        }
        panel.resetFrom(envSettings)
        panel.resetGlobalEnvsFrom(globalEnvSettings)

        val target = EnvironmentSettings()
        val globalTarget = IntelligentSettings()
        panel.applyTo(target)
        panel.applyGlobalEnvsTo(globalTarget)

        assertTrue(target.projectEnvironments.isNotBlank())
        assertTrue(globalTarget.globalEnvironments.isNotBlank())
    }
}
