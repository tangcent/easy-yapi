package com.itangcent.easyapi.exporter

import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.cache.ApiIndex
import com.itangcent.easyapi.dashboard.ApiScanner
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ExportContext
import com.itangcent.easyapi.exporter.model.ExportFormat
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.exporter.model.OutputConfig
import com.itangcent.easyapi.ide.support.SelectionScope
import com.itangcent.easyapi.settings.SettingBinder
import kotlinx.coroutines.withContext

/**
 * Orchestrates the API export process from scanning to output.
 *
 * This service coordinates:
 * 1. Scanning for API endpoints (from selection or index)
 * 2. Building the export context
 * 3. Invoking the appropriate exporter
 *
 * ## Usage
 * ```kotlin
 * val orchestrator = ExportOrchestrator.getInstance(project)
 * val result = orchestrator.orchestrateExport(
 *     selection = SelectionScope.FileScope(psiFile),
 *     format = ExportFormat.MARKDOWN
 * )
 * ```
 *
 * @see ApiExporter for format-specific export logic
 * @see ApiScanner for endpoint scanning
 */
@Service(Service.Level.PROJECT)
class ExportOrchestrator(private val project: Project) {
    
    private val apiScanner: ApiScanner = ApiScanner.getInstance(project)
    private val apiIndex: ApiIndex = ApiIndex.getInstance(project)
    private val exporterRegistry: ApiExporterRegistry = ApiExporterRegistry.getInstance(project)
    
    companion object {
        /**
         * Gets the export orchestrator instance for the given project.
         */
        fun getInstance(project: Project): ExportOrchestrator {
            return project.getService(ExportOrchestrator::class.java)
        }
    }
    
    /**
     * Orchestrates the complete export process from selection to output.
     *
     * @param selection The selection scope, or null to export all indexed endpoints
     * @param format The target export format
     * @param outputConfig Output configuration options
     * @return The export result
     */
    suspend fun orchestrateExport(
        selection: SelectionScope?,
        format: ExportFormat,
        outputConfig: OutputConfig = OutputConfig.DEFAULT,
        indicator: ProgressIndicator? = null
    ): ExportResult {
        indicator?.text = "Scanning for API endpoints..."
        indicator?.isIndeterminate = true
        val endpoints = scanEndpoints(selection, indicator)
        
        if (endpoints.isEmpty()) {
            return ExportResult.Error("No API endpoints found")
        }

        indicator?.text = "Exporting ${endpoints.size} endpoints..."
        indicator?.isIndeterminate = false
        indicator?.fraction = 0.0
        
        val context = buildExportContext(endpoints, format, outputConfig, indicator)
        
        val exporter = exporterRegistry.getExporter(format)
            ?: return ExportResult.Error("No exporter for format: $format")
        
        return exporter.export(context)
    }
    
    /**
     * Exports pre-collected endpoints to the specified format.
     *
     * Use this when endpoints have already been collected.
     *
     * @param endpoints The endpoints to export
     * @param format The target export format
     * @param outputConfig Output configuration options
     * @return The export result
     */
    suspend fun exportEndpoints(
        endpoints: List<ApiEndpoint>,
        format: ExportFormat,
        outputConfig: OutputConfig,
        indicator: ProgressIndicator? = null
    ): ExportResult {
        indicator?.text = "Exporting ${endpoints.size} endpoints..."
        indicator?.isIndeterminate = false
        indicator?.fraction = 0.0

        val context = buildExportContext(endpoints, format, outputConfig, indicator)
        val exporter = exporterRegistry.getExporter(format)
            ?: return ExportResult.Error("No exporter for format: $format")
        
        return exporter.export(context)
    }
    
    /**
     * Scans for API endpoints from the given selection or index.
     */
    private suspend fun scanEndpoints(
        selection: SelectionScope?,
        indicator: ProgressIndicator? = null
    ): List<ApiEndpoint> {
        if (selection != null) {
            val classes = withContext(IdeDispatchers.ReadAction) {
                selection.classes().toList()
            }
            if (classes.isNotEmpty()) {
                return apiScanner.scanClasses(classes, indicator).toList()
            }
        }
        return apiIndex.endpoints()
    }
    
    /**
     * Builds the export context with all required dependencies.
     */
    private fun buildExportContext(
        endpoints: List<ApiEndpoint>,
        format: ExportFormat,
        outputConfig: OutputConfig,
        indicator: ProgressIndicator? = null
    ): ExportContext {
        val settings = SettingBinder.getInstance(project).read()
        return ExportContext(
            project = project,
            endpoints = endpoints,
            exportFormat = format,
            settings = settings,
            outputConfig = outputConfig,
            indicator = indicator
        )
    }
}
