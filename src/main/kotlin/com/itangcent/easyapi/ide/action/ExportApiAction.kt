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
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.cache.ApiIndex
import com.itangcent.easyapi.core.threading.backgroundAsync
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.dashboard.ApiScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
class ExportApiAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selection = SelectedHelper.resolveSelection(e)

        backgroundAsync {
            val endpointCount = withContext(IdeDispatchers.ReadAction) {
                val apiIndex = ApiIndex.getInstance(project)
                val scanner = ApiScanner.getInstance(project)
                if (selection != null) {
                    val classes = selection.classes().toList()
                    if (classes.isNotEmpty()) {
                        scanner.scanClasses(classes).toList().size
                    } else {
                        apiIndex.endpoints().size
                    }
                } else {
                    apiIndex.endpoints().size
                }
            }

            swing {
                val result = ExportDialog.show(project, endpointCount)
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
                val exportResult = orchestrator.orchestrateExport(
                    selection, dialogResult.format, dialogResult.outputConfig, indicator
                )

                swing {
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
                val handled = exporter?.handleExportResult(project, result) ?: false
                if (!handled) {
                    val message = "Successfully exported ${result.count} endpoints to ${result.target}"
                    Messages.showInfoMessage(project, message, "Export Successful")
                }
            }

            is ExportResult.Error -> {
                Messages.showErrorDialog(project, result.message, "Export Failed")
            }

            is ExportResult.Cancelled -> {}
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
