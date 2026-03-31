package com.itangcent.easyapi.exporter

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.exporter.curl.CurlExporter
import com.itangcent.easyapi.exporter.httpclient.HttpClientExporter
import com.itangcent.easyapi.exporter.markdown.MarkdownExporter
import com.itangcent.easyapi.exporter.model.ExportFormat
import com.itangcent.easyapi.exporter.postman.PostmanExporter
import com.itangcent.easyapi.exporter.yapi.YapiExporter

/**
 * Registry for API exporters, providing access to exporter instances by format.
 *
 * This service maintains references to all available exporters and provides
 * lookup functionality based on export format.
 *
 * ## Usage
 * ```kotlin
 * val registry = ApiExporterRegistry.getInstance(project)
 * val exporter = registry.getExporter(ExportFormat.MARKDOWN)
 * exporter?.export(context)
 * ```
 *
 * @see ApiExporter
 * @see ExportFormat
 */
@Service(Service.Level.PROJECT)
class ApiExporterRegistry(private val project: Project) {
    
    companion object {
        /**
         * Gets the exporter registry instance for the given project.
         */
        fun getInstance(project: Project): ApiExporterRegistry {
            return project.getService(ApiExporterRegistry::class.java)
        }
    }
    
    /**
     * Gets the exporter for the specified format.
     *
     * @param format The export format
     * @return The exporter instance, or null if format is not supported
     */
    fun getExporter(format: ExportFormat): ApiExporter? {
        return when (format) {
            ExportFormat.MARKDOWN -> MarkdownExporter.getInstance(project)
            ExportFormat.YAPI -> YapiExporter.getInstance(project)
            ExportFormat.POSTMAN -> PostmanExporter.getInstance(project)
            ExportFormat.CURL -> CurlExporter.getInstance(project)
            ExportFormat.HTTP_CLIENT -> HttpClientExporter.getInstance(project)
        }
    }
    
    /**
     * Gets all available exporters.
     *
     * @return Collection of all registered exporters
     */
    fun getAllExporters(): Collection<ApiExporter> {
        return listOf(
            MarkdownExporter.getInstance(project),
            YapiExporter.getInstance(project),
            PostmanExporter.getInstance(project),
            CurlExporter.getInstance(project),
            HttpClientExporter.getInstance(project)
        )
    }
}
