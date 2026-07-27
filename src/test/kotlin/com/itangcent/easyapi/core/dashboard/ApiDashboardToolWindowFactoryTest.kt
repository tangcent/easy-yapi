package com.itangcent.easyapi.core.dashboard

import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.update
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class ApiDashboardToolWindowFactoryTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testFactoryExists() {
        val factory = ApiDashboardToolWindowFactory()
        assertNotNull("Factory should be instantiable", factory)
    }

    fun testCreateContentDoesNotThrowWhenApiScanEnabled() {
        settingBinder.update(GeneralSettings::class) {
            apiScanEnabled = true
        }

        val toolWindow = registerDashboardToolWindow()
        try {
            val factory = ApiDashboardToolWindowFactory()
            factory.createToolWindowContent(project, toolWindow)
        } catch (e: Exception) {
            fail("createToolWindowContent should not throw when apiScanEnabled is true: ${e.message}")
        }
    }

    fun testCreateContentDoesNotThrowWhenApiScanDisabled() {
        settingBinder.update(GeneralSettings::class) {
            apiScanEnabled = false
        }

        val toolWindow = registerDashboardToolWindow()
        try {
            val factory = ApiDashboardToolWindowFactory()
            factory.createToolWindowContent(project, toolWindow)
        } catch (e: Exception) {
            fail("createToolWindowContent should not throw when apiScanEnabled is false: ${e.message}")
        } finally {
            // Reset for subsequent tests
            settingBinder.update(GeneralSettings::class) {
                apiScanEnabled = true
            }
        }
    }

    /**
     * Registers a test "API Dashboard" tool window so the factory can populate
     * it. The tool window is disposed with the project at tearDown.
     */
    private fun registerDashboardToolWindow(): ToolWindow =
        ToolWindowManager.getInstance(project)
            .registerToolWindow(
                RegisterToolWindowTask("API Dashboard")
            )
}
