package com.itangcent.easyapi.core.dashboard

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.onSettingsChanged
import com.itangcent.easyapi.core.settings.settings

/**
 * Factory for creating the API Dashboard tool window in IntelliJ IDEA.
 *
 * The tool window is registered in plugin.xml, but its visibility is gated by
 * the `apiScanEnabled` master toggle: when API scanning is disabled, the API
 * index that the dashboard consumes is never built, so the dashboard would be
 * an empty shell — it is hidden from the stripe via [ToolWindow.setAvailable]
 * until scanning is re-enabled. Toggling the setting at runtime re-shows/hides
 * the tool window without a project restart.
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

        // One disposable ties together the panel disposal and the settings
        // listener, so both are cleaned up when the content is removed.
        val disposable = Disposer.newDisposable()
        Disposer.register(disposable, Disposable { panel.dispose() })

        val content = toolWindow.contentManager.factory.createContent(panel, "", false)
        content.setDisposer(disposable)
        toolWindow.contentManager.addContent(content)

        // Apply the master-toggle availability now, then keep it in sync with
        // settings changes for the lifetime of the tool window.
        applyAvailability(project, toolWindow)
        project.onSettingsChanged(disposable) {
            applyAvailability(project, toolWindow)
        }
    }

    /**
     * Sets the tool window's availability based on the current value of
     * `apiScanEnabled`. When unavailable, the stripe button is hidden and the
     * dashboard cannot be activated.
     */
    private fun applyAvailability(project: Project, toolWindow: ToolWindow) {
        val enabled = project.settings<GeneralSettings>().apiScanEnabled
        toolWindow.setAvailable(enabled)
    }
}
