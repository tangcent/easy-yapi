package com.itangcent.easyapi.logging

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import com.itangcent.easyapi.core.threading.swingAsync
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.settings.onSettingsChanged
import com.itangcent.easyapi.settings.settings

/**
 * Dynamically shows/hides the "EasyAPI" console tool window based on the configured
 * log level.
 *
 * - When `logLevel > [LogLevel.ERROR.threshold]` (i.e. SILENT), the tool window button
 *   is hidden via [com.intellij.openapi.wm.ToolWindow.setAvailable] — the console is
 *   never used in this mode ([IdeaConsoleProvider] routes output to [IdeaLogConsole]).
 * - When `logLevel <= ERROR.threshold`, the tool window button is shown.
 *
 * Listens to settings changes so the visibility updates immediately when the user
 * adjusts the log level, without requiring a project reload.
 */
class EasyApiConsoleToolWindowActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        updateAvailability(project)

        project.onSettingsChanged {
            updateAvailability(project)
        }
    }

    private fun updateAvailability(project: Project) {
        val logLevel = project.settings.logLevel
        val available = logLevel <= LogLevel.ERROR.threshold

        // ToolWindow.setAvailable must be called on EDT.
        swingAsync {
            val toolWindow = ToolWindowManager.getInstance(project)
                .getToolWindow(DefaultIdeaConsole.WINDOW_ID) ?: return@swingAsync
            toolWindow.setAvailable(available, null)
        }
    }
}
