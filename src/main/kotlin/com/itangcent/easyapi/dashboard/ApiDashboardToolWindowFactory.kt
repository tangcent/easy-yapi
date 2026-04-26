package com.itangcent.easyapi.dashboard

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

/**
 * Factory for creating the API Dashboard tool window in IntelliJ IDEA.
 * 
 * This factory is registered in plugin.xml and creates the tool window content
 * when the user opens the API Dashboard. It connects the panel to the project-level
 * service for state management.
 */
class ApiDashboardToolWindowFactory : ToolWindowFactory {
    /**
     * Creates the content for the API Dashboard tool window.
     * Initializes the panel and registers it with the project service.
     * 
     * @param project The current IntelliJ project
     * @param toolWindow The tool window to populate
     */
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ApiDashboardPanel(project)
        val service = ApiDashboardService.getInstance(project)
        service.setDashboardPanel(panel)

        val content = toolWindow.contentManager.factory.createContent(panel, "", false)
        content.setDisposer(Disposable { panel.dispose() })
        toolWindow.contentManager.addContent(content)
    }
}
