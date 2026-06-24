package com.itangcent.easyapi.ide.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.itangcent.easyapi.exporter.ExportOrchestrator
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.ide.DumbModeHelper
import com.itangcent.easyapi.ide.dialog.ExportDialog
import com.itangcent.easyapi.ide.dialog.ExportDialogResult
import com.itangcent.easyapi.ide.support.SelectedHelper
import com.itangcent.easyapi.ide.support.SelectionScope
import com.itangcent.easyapi.ide.support.runWithProgress
import com.itangcent.easyapi.cache.api.ApiIndex
import com.itangcent.easyapi.core.threading.backgroundAsync
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.dashboard.ApiScanner
import com.itangcent.easyapi.ide.dialog.ExportDialogPreferences
import com.itangcent.easyapi.ide.dialog.ExportDialogPreferencesPersistence
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.logging.console

class ExportApiAction : AnAction(), IdeaLog {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val console = project.console
        console.debug("ExportApiAction.actionPerformed: project=${project.name}")
        val selection = SelectedHelper.resolveSelection(e)

        backgroundAsync {
            if (!DumbModeHelper.waitForSmartModeOrNotify(project)) return@backgroundAsync

            val apiIndex = ApiIndex.getInstance(project)
            val scanner = ApiScanner.getInstance(project)
            val endpoints = if (selection != null) {
                val classes = selection.classes().toList()
                if (classes.isNotEmpty()) {
                    scanner.scanClasses(classes).toList()
                } else {
                    apiIndex.endpoints()
                }
            } else {
                apiIndex.endpoints()
            }

            swing {
                val result = ExportDialog.show(project, endpoints.size, endpoints)
                if (result != null) {
                    performExport(project, selection, result)
                }
            }
        }
    }

    private fun performExport(
        project: Project,
        selection: SelectionScope?,
        dialogResult: ExportDialogResult
    ) {
        backgroundAsync {
            runWithProgress(project, "Exporting APIs...") { indicator ->
                val orchestrator = ExportOrchestrator.getInstance(project)
                val channelId = dialogResult.channelId

                val endpoints = if (dialogResult.selectedEndpoints.isNotEmpty()) {
                    dialogResult.selectedEndpoints.map { it.endpoint }
                } else {
                    val apiIndex = ApiIndex.getInstance(project)
                    if (selection != null) {
                        val scanner = ApiScanner.getInstance(project)
                        val classes = selection.classes().toList()
                        if (classes.isNotEmpty()) scanner.scanClasses(classes).toList()
                        else apiIndex.endpoints()
                    } else {
                        apiIndex.endpoints()
                    }
                }

                val exportResult = orchestrator.exportViaChannel(
                    channelId, endpoints, dialogResult.channelConfig, indicator
                )
                handleExportResult(project, exportResult)
            }
        }
    }

    private suspend fun handleExportResult(
        project: Project,
        result: ExportResult
    ) {
        when (result) {
            is ExportResult.Error -> {
                swing {
                    Messages.showErrorDialog(project, result.message, "Export Failed")
                }
                if (result.message.startsWith("No channel registered for id:")) {
                    val prefsPersistence = ExportDialogPreferencesPersistence(project)
                    prefsPersistence.save(ExportDialogPreferences(lastExportFormat = null))
                }
            }

            is ExportResult.Success, is ExportResult.Cancelled -> {}
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
