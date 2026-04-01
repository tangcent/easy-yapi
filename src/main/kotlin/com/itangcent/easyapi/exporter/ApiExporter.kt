package com.itangcent.easyapi.exporter

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.exporter.model.ExportContext
import com.itangcent.easyapi.exporter.model.ExportFormat
import com.itangcent.easyapi.exporter.model.ExportResult

/**
 * Interface for API exporters that convert API endpoints to various formats.
 *
 * Implementations handle the conversion of collected API endpoints
 * to specific output formats like Markdown, Postman, YAPI, etc.
 *
 * ## Implementations
 * - [MarkdownExporter] - Exports to Markdown format
 * - [PostmanExporter] - Exports to Postman collection
 * - [YapiExporter] - Exports to YAPI platform
 * - [CurlExporter] - Exports as cURL commands
 * - [HttpClientExporter] - Exports as IntelliJ HTTP Client files
 *
 * @see ApiExporterRegistry for getting exporter instances
 * @see ExportOrchestrator for coordinating exports
 */
interface ApiExporter {
    /**
     * The export format this exporter handles.
     */
    val format: ExportFormat
    
    /**
     * Exports API endpoints according to this exporter's format.
     *
     * @param context The export context containing endpoints and configuration
     * @return The export result (success with output or error)
     */
    suspend fun export(context: ExportContext): ExportResult
    
    /**
     * Handles the export result after successful export.
     *
     * Override this to perform post-export actions like:
     * - Writing to file
     * - Uploading to remote server
     * - Showing in UI
     *
     * @param project The IntelliJ project
     * @param result The successful export result
     * @return true if the result was handled, false otherwise
     */
    suspend fun handleExportResult(project: Project, result: ExportResult.Success): Boolean {
        return false
    }
}
