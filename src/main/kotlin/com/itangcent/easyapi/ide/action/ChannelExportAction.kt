package com.itangcent.easyapi.ide.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.itangcent.easyapi.core.threading.backgroundAsync
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.dashboard.ApiScanner
import com.itangcent.easyapi.exporter.ExportOrchestrator
import com.itangcent.easyapi.ide.DumbModeHelper
import com.itangcent.easyapi.ide.support.SelectedHelper
import com.itangcent.easyapi.ide.support.runWithProgress
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.logging.console

class ChannelExportAction(
    private val channelId: String,
    channelDisplayName: String
) : AnAction(channelDisplayName), IdeaLog {

    private val channelDisplayName = channelDisplayName

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val console = project.console
        console.info("ChannelExportAction.actionPerformed: channel=$channelId, project=${project.name}")
        val selection = SelectedHelper.resolveSelection(e)

        backgroundAsync {
            if (!DumbModeHelper.waitForSmartModeOrNotify(project)) return@backgroundAsync

            val scanner = ApiScanner.getInstance(project)
            // scanSelection respects method-level selections (issue #1407):
            // when the user selects specific controller methods, only those
            // methods' endpoints are exported directly, without a dialog.
            val endpoints = scanner.scanSelection(selection)

            if (endpoints.isEmpty()) {
                swing {
                    com.intellij.openapi.ui.Messages.showInfoMessage(
                        project, "No API endpoints found.", "Export API"
                    )
                }
                return@backgroundAsync
            }

            val orchestrator = ExportOrchestrator.getInstance(project)
            val result = runWithProgress(
                project, "Exporting APIs via $channelDisplayName..."
            ) { indicator ->
                orchestrator.exportViaChannel(channelId, endpoints, indicator = indicator)
            }

            when (result) {
                is com.itangcent.easyapi.exporter.model.ExportResult.Error -> {
                    swing {
                        com.intellij.openapi.ui.Messages.showErrorDialog(
                            project, result.message, "Export Failed"
                        )
                    }
                }
                is com.itangcent.easyapi.exporter.model.ExportResult.Success,
                is com.itangcent.easyapi.exporter.model.ExportResult.Cancelled -> {}
            }
        }
    }

    override fun toString(): String = "Export to $channelDisplayName"
}
