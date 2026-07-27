package com.itangcent.easyapi.core.ide.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.logging.console
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.settings

/**
 * Action to open the API Dashboard tool window.
 *
 * Activates the "API Dashboard" tool window, which provides a centralized
 * view for browsing and testing API endpoints. No-ops when API scanning is
 * disabled, since the dashboard would be an empty shell without the index.
 */
class OpenApiDashboardAction : AnAction(), IdeaLog {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val console = project.console
        console.info("OpenApiDashboardAction.actionPerformed: project=${project.name}")
        // The tool window is hidden via setAvailable(false) when scanning is
        // off; bail out early to avoid activating an unavailable window.
        if (!project.settings<GeneralSettings>().apiScanEnabled) {
            return
        }
        ToolWindowManager.getInstance(project).getToolWindow("API Dashboard")?.activate(null)
    }
}
