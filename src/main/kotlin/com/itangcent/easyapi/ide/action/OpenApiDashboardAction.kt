package com.itangcent.easyapi.ide.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Action to open the API Dashboard tool window.
 *
 * Activates the "API Dashboard" tool window, which provides a centralized
 * view for browsing and testing API endpoints.
 */
class OpenApiDashboardAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ToolWindowManager.getInstance(project).getToolWindow("API Dashboard")?.activate(null)
    }
}
