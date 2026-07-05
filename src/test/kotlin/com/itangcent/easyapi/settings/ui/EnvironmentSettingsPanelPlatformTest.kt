package com.itangcent.easyapi.settings.ui

import com.itangcent.easyapi.settings.module.EnvironmentSettings
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
        panel.resetFrom(envSettings)

        val target = EnvironmentSettings()
        panel.applyTo(target)

        assertEquals("", target.projectEnvironments)
        assertEquals("", target.globalEnvironments)
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
        val settings = EnvironmentSettings().apply {
            this.globalEnvironments = globalEnvJson
        }
        panel.resetFrom(settings)

        val target = EnvironmentSettings()
        panel.applyTo(target)

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
            globalEnvironments = ""
        }
        panel.resetFrom(envSettings)

        val target = EnvironmentSettings()
        panel.applyTo(target)

        assertEquals("", target.projectEnvironments)
        assertEquals("", target.globalEnvironments)
    }

    fun testResetFromMalformedJson() {
        val envSettings = EnvironmentSettings().apply {
            projectEnvironments = "not-valid-json"
            globalEnvironments = "also-not-valid"
        }
        panel.resetFrom(envSettings)
        // Should not throw, just ignore malformed JSON

        val target = EnvironmentSettings()
        panel.applyTo(target)
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
        val settings = EnvironmentSettings().apply {
            this.projectEnvironments = projectEnvJson
            this.globalEnvironments = globalEnvJson
        }
        panel.resetFrom(settings)

        val target = EnvironmentSettings()
        panel.applyTo(target)

        assertTrue(target.projectEnvironments.isNotBlank())
        assertTrue(target.globalEnvironments.isNotBlank())
    }
}
