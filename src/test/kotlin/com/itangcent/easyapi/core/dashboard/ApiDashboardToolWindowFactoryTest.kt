package com.itangcent.easyapi.core.dashboard

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.registerServiceInstance
import com.itangcent.easyapi.core.internal.threading.swingBlocking
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.update
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class ApiDashboardToolWindowFactoryTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun tearDown() {
        try {
            swingBlocking {
                ToolWindowManager.getInstance(project)
                    .getToolWindow("API Explorer")
                    ?.contentManager
                    ?.let { contentManager ->
                        contentManager.contents.forEach { content ->
                            contentManager.removeContent(content, true)
                        }
                    }
            }
            settingBinder.update(GeneralSettings::class) {
                apiScanEnabled = true
            }
        } finally {
            super.tearDown()
        }
    }

    fun testDisabledScanningKeepsDashboardContentReachable() {
        settingBinder.update(GeneralSettings::class) {
            apiScanEnabled = false
        }
        val toolWindow = registerDashboardToolWindow()
        var availabilityRequests = 0
        val factory = ApiDashboardToolWindowFactory { requestedToolWindow ->
            assertSame("Factory should keep its own tool window available", toolWindow, requestedToolWindow)
            availabilityRequests++
            requestedToolWindow.setAvailable(true)
        }

        swingBlocking {
            factory.createToolWindowContent(project, toolWindow)
        }

        assertEquals("Factory should request availability exactly once", 1, availabilityRequests)

        assertEquals(
            "Dashboard should retain its content entry while scanning is disabled",
            1,
            swingBlocking { toolWindow.contentManager.contentCount }
        )
        val panel = swingBlocking {
            toolWindow.contentManager.contents.single().component as ApiDashboardPanel
        }
        assertTrue(
            "Disabled dashboard content should expose paused state",
            swingBlocking { panel.dashboardStatusText() }.startsWith("Paused")
        )
    }

    fun testEnabledScanningKeepsDashboardContentReachable() {
        settingBinder.update(GeneralSettings::class) {
            apiScanEnabled = true
        }
        val toolWindow = registerDashboardToolWindow()

        swingBlocking {
            ApiDashboardToolWindowFactory().createToolWindowContent(project, toolWindow)
        }

        assertEquals(
            "Dashboard should contain the cache viewer",
            1,
            swingBlocking { toolWindow.contentManager.contentCount }
        )
        assertTrue(
            "Factory should register the dashboard panel",
            swingBlocking {
                toolWindow.contentManager.contents.single().component is ApiDashboardPanel
            }
        )
    }

    fun testContentDisposalStopsDashboardService() {
        val originalService = ApiDashboardService.getInstance(project)
        val dashboardService = mock<ApiDashboardService>()
        project.registerServiceInstance(ApiDashboardService::class.java, dashboardService)

        try {
            val toolWindow = registerDashboardToolWindow()
            swingBlocking {
                ApiDashboardToolWindowFactory().createToolWindowContent(project, toolWindow)
            }

            val (content, panel) = swingBlocking {
                val content = toolWindow.contentManager.contents.single()
                content to content.component as ApiDashboardPanel
            }
            verify(dashboardService).setDashboardPanel(panel)

            swingBlocking {
                Disposer.dispose(content.disposer!!)
            }

            verify(dashboardService).stop()
        } finally {
            project.registerServiceInstance(ApiDashboardService::class.java, originalService)
        }
    }

    private fun registerDashboardToolWindow(): ToolWindow = swingBlocking {
        ToolWindowManager.getInstance(project)
            .registerToolWindow(RegisterToolWindowTask("API Explorer"))
    }
}
