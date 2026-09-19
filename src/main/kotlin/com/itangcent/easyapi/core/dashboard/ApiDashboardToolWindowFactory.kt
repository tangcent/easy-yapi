package com.itangcent.easyapi.core.dashboard

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

/**
 * Creates an always-available API Explorer cache viewer.
 *
 * The tool window's id (`plugin.xml`) is also its visible label — the platform
 * shows the raw id unless a `toolwindow.stripe.<id>` bundle key overrides it, and
 * this plugin declares no bundle. Production code looks the window up by that
 * same string; `ToolWindowLookupGuardTest` keeps the two in step.
 *
 * Scanning state is represented inside [ApiDashboardPanel]. Paused scanning
 * never removes access to retained endpoints or the manual refresh action. The
 * content-scoped disposable also stops the dashboard service when the content is
 * removed.
 */
class ApiDashboardToolWindowFactory internal constructor(
    private val ensureAvailable: (ToolWindow) -> Unit
) : ToolWindowFactory {

    constructor() : this({ toolWindow -> toolWindow.setAvailable(true) })
    /**
     * Creates the dashboard content and keeps the tool window reachable.
     *
     * @param project The current IntelliJ project
     * @param toolWindow The tool window to populate
     * @requires Swing context
     */
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ApiDashboardPanel(project)
        val dashboardService = ApiDashboardService.getInstance(project)
        dashboardService.setDashboardPanel(panel)

        val disposable = Disposer.newDisposable()
        Disposer.register(disposable, Disposable {
            panel.dispose()
            dashboardService.stop()
        })

        toolWindow.contentManager.factory.createContent(panel, "", false).also { content ->
            content.setDisposer(disposable)
            toolWindow.contentManager.addContent(content)
        }
        ensureAvailable(toolWindow)
    }
}
