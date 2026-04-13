package com.itangcent.easyapi.ide.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.event.ActionCompletedTopic
import com.itangcent.easyapi.core.event.ActionCompletedTopic.Companion.syncPublish
import com.itangcent.easyapi.core.threading.backgroundAsync
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.exporter.ApiExporterRegistry
import com.itangcent.easyapi.exporter.ExportOrchestrator
import com.itangcent.easyapi.exporter.grpc.GrpcServiceRecognizer
import com.itangcent.easyapi.exporter.model.ExportFormat
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.exporter.model.OutputConfig
import com.itangcent.easyapi.ide.support.SelectedHelper
import com.itangcent.easyapi.ide.support.SelectionScope
import com.itangcent.easyapi.ide.support.runWithProgress
import com.itangcent.easyapi.logging.IdeaConsoleProvider
import com.itangcent.easyapi.logging.IdeaLog
import kotlin.coroutines.cancellation.CancellationException

/**
 * Base class for API export actions.
 *
 * Provides common functionality for exporting APIs to various formats:
 * - Progress indicator management
 * - Selection resolution
 * - Result handling
 *
 * Subclasses must implement:
 * - [exportFormat]: The target export format
 * - [actionName]: The display name for the action
 *
 * @see ExportOrchestrator for the export process
 * @see ExportFormat for available formats
 */
abstract class BaseExportAction : EasyApiAction(), IdeaLog {

    /**
     * The export format for this action.
     */
    abstract val exportFormat: ExportFormat

    /**
     * The display name shown in the progress dialog.
     */
    protected abstract val actionName: String

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabled = false
            return
        }

        // Check if format supports the selected elements
        val selection = SelectedHelper.resolveSelection(e)
        val hasGrpc = selection?.let { hasGrpcEndpoints(it) } ?: false

        e.presentation.isEnabled = if (hasGrpc) {
            exportFormat.supportsGrpc
        } else {
            exportFormat.supportsHttp
        }
    }

    /**
     * Quick check if the selection might contain gRPC endpoints.
     * This is a heuristic check without full scanning.
     */
    private fun hasGrpcEndpoints(selection: SelectionScope): Boolean {
        // Check if any selected class is a gRPC service
        for (psiClass in selection.classes()) {
            if (GrpcServiceRecognizer.extendsBindableService(psiClass)) {
                return true
            }
        }
        return false
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selection = SelectedHelper.resolveSelection(e)

        backgroundAsync {
            runWithProgress(project, actionName) { indicator ->
                performExport(project, selection, indicator)
            }
        }
    }

    private suspend fun performExport(
        project: Project,
        selection: SelectionScope?,
        indicator: ProgressIndicator
    ) {
        try {
            val orchestrator = ExportOrchestrator.getInstance(project)
            val result = orchestrator.orchestrateExport(selection, exportFormat, indicator = indicator)

            handleExportResult(project, result)
        } catch (ce: CancellationException) {
            LOG.info("Export cancelled")
            throw ce
        } catch (ex: Throwable) {
            LOG.warn("Export failed", ex)
            IdeaConsoleProvider.getInstance(project).getConsole().error("Export failed", ex)
            swing {
                showExportError(project, ex.message ?: "Unknown error")
            }
        } finally {
            project.syncPublish(ActionCompletedTopic.TOPIC)
        }
    }

    protected open suspend fun handleExportResult(project: Project, result: ExportResult) {
        when (result) {
            is ExportResult.Success -> {
                val exporterRegistry = ApiExporterRegistry.getInstance(project)
                val exporter = exporterRegistry.getExporter(exportFormat)

                val handled = try {
                    exporter?.handleExportResult(project, result, OutputConfig.DEFAULT) ?: false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LOG.warn("Failed to handle export result", e)
                    IdeaConsoleProvider.getInstance(project).getConsole()
                        .error("Failed to save export result", e)
                    false
                }

                if (!handled) {
                    swing {
                        showSuccessMessage(project, result)
                    }
                }
            }

            is ExportResult.Cancelled -> {
            }

            is ExportResult.Error -> {
                swing {
                    showExportError(project, result.message)
                }
            }
        }
    }

    protected open fun showSuccessMessage(project: Project, result: ExportResult.Success) {
        val message = buildString {
            append("Successfully exported ${result.count} endpoints to ${result.target}")
            result.metadata?.formatDisplay()?.let { append(" $it") }
        }
        com.intellij.openapi.ui.Messages.showInfoMessage(
            project,
            message,
            "Export API"
        )
    }

    protected open fun showExportError(project: Project, message: String) {
        com.intellij.openapi.ui.Messages.showErrorDialog(
            project,
            "Export failed: $message",
            "Export API"
        )
    }
}
