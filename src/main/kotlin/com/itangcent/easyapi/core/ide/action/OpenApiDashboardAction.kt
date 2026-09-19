package com.itangcent.easyapi.core.ide.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.itangcent.easyapi.core.feature.CoreFeatureIds
import com.itangcent.easyapi.core.feature.FeatureStateService
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.logging.console

/**
 * Opens the API Explorer retained-cache viewer and manual refresh entry point.
 *
 * The explorer remains reachable while scanning is paused.
 */
class OpenApiDashboardAction internal constructor(
    private val openDashboard: (Project) -> Unit
) : AnAction(), IdeaLog {

    constructor() : this({ project ->
        ToolWindowManager.getInstance(project)
            .getToolWindow("API Explorer")
            ?.activate(null)
    })

    /** @requires Swing context */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val scanState = if (
            FeatureStateService.getInstance(project).isEffective(CoreFeatureIds.API_SCANNING)
        ) {
            "active"
        } else {
            "paused"
        }
        project.console.info(
            "OpenApiDashboardAction.actionPerformed: project=${project.name} scanState=$scanState"
        )
        openDashboard(project)
    }
}
