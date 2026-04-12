package com.itangcent.easyapi.ide.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.itangcent.easyapi.exporter.ApiExporterRegistry
import com.itangcent.easyapi.exporter.ExportOrchestrator
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.ide.dialog.ExportDialog
import com.itangcent.easyapi.ide.dialog.ExportDialogResult
import com.itangcent.easyapi.ide.support.SelectedHelper
import com.itangcent.easyapi.ide.support.SelectionScope
import com.itangcent.easyapi.ide.support.runWithProgress
import com.itangcent.easyapi.cache.ApiIndex
import com.itangcent.easyapi.core.threading.backgroundAsync
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.dashboard.ApiScanner
import com.itangcent.easyapi.logging.IdeaLog
import kotlinx.coroutines.CancellationException

/**
 * Action for exporting APIs with a format selection dialog.
 *
 * Shows an [ExportDialog] allowing the user to choose the export format
 * and configure output options before initiating the export.
 *
 * ## Supported Formats
 * - Markdown
 * - YAPI
 * - Postman
 *
 * @see ExportDialog for the configuration dialog
 * @see ExportOrchestrator for the export process
 */
class ExportApiAction : AnAction(), IdeaLog {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selection = SelectedHelper.resolveSelection(e)

        backgroundAsync {
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

                if (dialogResult.selectedEndpoints.isNotEmpty()) {
                    val endpoints = dialogResult.selectedEndpoints.map { it.endpoint }
                    val exportResult = orchestrator.exportEndpoints(
                        endpoints, dialogResult.format, dialogResult.outputConfig, indicator
                    )
                    handleExportResult(project, exportResult, dialogResult)
                } else {
                    val exportResult = orchestrator.orchestrateExport(
                        selection, dialogResult.format, dialogResult.outputConfig, indicator
                    )
                    handleExportResult(project, exportResult, dialogResult)
                }
            }
        }
    }

    private suspend fun handleExportResult(
        project: Project,
        result: ExportResult,
        dialogResult: ExportDialogResult
    ) {
        when (result) {
            is ExportResult.Success -> {
                val exporter = ApiExporterRegistry.getInstance(project)
                    .getExporter(dialogResult.format)
                val handled = try {
                    exporter?.handleExportResult(project, result, dialogResult.outputConfig) ?: false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LOG.warn("Failed to handle export result", e)
                    false
                }
                if (!handled) {
                    swing {
                        val message = "Successfully exported ${result.count} endpoints to ${result.target}"
                        Messages.showInfoMessage(project, message, "Export Successful")
                    }
                }
            }

            is ExportResult.Error -> {
                swing {
                    Messages.showErrorDialog(project, result.message, "Export Failed")
                }
            }

            is ExportResult.Cancelled -> {}
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
