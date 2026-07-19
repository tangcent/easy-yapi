package com.itangcent.easyapi.core.ide.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.itangcent.easyapi.core.internal.threading.backgroundAsync
import com.itangcent.easyapi.core.internal.threading.swing
import com.itangcent.easyapi.core.dashboard.ApiScanner
import com.itangcent.easyapi.core.export.ExportOrchestrator
import com.itangcent.easyapi.channel.spi.ChannelRegistry
import com.itangcent.easyapi.core.ide.DumbModeHelper
import com.itangcent.easyapi.core.ide.support.SelectedHelper
import com.itangcent.easyapi.core.ide.support.runWithProgress
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.logging.console

class ChannelExportAction(
    private val channelId: String,
    channelDisplayName: String
) : AnAction(channelDisplayName), IdeaLog {

    private val channelDisplayName = channelDisplayName

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null
        if (project != null) {
            // Re-evaluate visibility per-display-context (NOT templatePresentation,
            // which the platform forbids mutating — Presentation.assertNotTemplatePresentation).
            // This is the chokepoint for "hide not unregister" (Decision 4): a
            // disabled channel's action stays registered (keymap IDs remain
            // stable) but is hidden from the menu because isVisible resolves to false.
            val registry = ChannelRegistry.getInstance(project)
            val channel = registry.getChannel(channelId)
            e.presentation.isVisible = channel?.let { registry.isEnabled(it) } ?: true
        }
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
                is com.itangcent.easyapi.core.export.ExportResult.Error -> {
                    swing {
                        com.intellij.openapi.ui.Messages.showErrorDialog(
                            project, result.message, "Export Failed"
                        )
                    }
                }
                is com.itangcent.easyapi.core.export.ExportResult.Success,
                is com.itangcent.easyapi.core.export.ExportResult.Cancelled -> {}
            }
        }
    }

    override fun toString(): String = "Export to $channelDisplayName"
}
