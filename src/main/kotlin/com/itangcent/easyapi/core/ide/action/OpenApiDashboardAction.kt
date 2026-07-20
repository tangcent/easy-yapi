package com.itangcent.easyapi.core.ide.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.logging.console

/**
 * Action to open the API Dashboard tool window.
 *
 * Activates the "API Dashboard" tool window, which provides a centralized
 * view for browsing and testing API endpoints.
 */
class OpenApiDashboardAction : AnAction(), IdeaLog {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val console = project.console
        console.info("OpenApiDashboardAction.actionPerformed: project=${project.name}")
        ToolWindowManager.getInstance(project).getToolWindow("API Dashboard")?.activate(null)
    }
}
