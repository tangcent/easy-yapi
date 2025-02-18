package com.itangcent.idea.plugin.api.dashboard

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

class ApiDashboardToolWindow : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val dashboardPanel = ApiDashboardPanel(project)
        val content = toolWindow.contentManager.factory.createContent(dashboardPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
