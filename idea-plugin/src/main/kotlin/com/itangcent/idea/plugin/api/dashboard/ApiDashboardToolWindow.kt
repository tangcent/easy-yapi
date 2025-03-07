package com.itangcent.idea.plugin.api.dashboard

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener

class ApiDashboardToolWindow : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val dashboardPanel = ApiDashboardPanel(project)
        val content = toolWindow.contentManager.factory.createContent(dashboardPanel, "", false)

        // Add content listener to handle disposal
        toolWindow.contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun contentRemoved(event: ContentManagerEvent) {
                if (event.content == content) {
                    dashboardPanel.dispose()
                }
            }
        })

        // Add tool window visibility listener
        project.messageBus.connect().subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
            override fun stateChanged(toolWindowManager: ToolWindowManager) {
                if (toolWindow.isVisible) {
                    // When tool window becomes visible, ensure ActionContext is active
                    ApiDashboardService.getInstance(project).ensureActionContextActive()
                }
            }
        })

        toolWindow.contentManager.addContent(content)
    }
}
