package com.itangcent.easyapi.exporter.model

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.settings.Settings

/**
 * Context for an API export operation.
 *
 * Contains all information needed for exporting APIs:
 * - The project and source classes
 * - The endpoints to export
 * - Export format and output configuration
 * - Settings and action context
 *
 * @param project The IntelliJ project
 * @param endpoints All available endpoints
 * @param selectedEndpoints User-selected endpoints (if any)
 * @param sourceClasses The source PSI classes
 * @param settings Plugin settings
 * @param exportFormat The target export format
 * @param outputConfig Output configuration options
 * @param actionContext The action context for the export operation
 * @param indicator Optional progress indicator for reporting progress
 */
data class ExportContext(
    val project: Project,
    val endpoints: List<ApiEndpoint>,
    val selectedEndpoints: List<ApiEndpoint> = emptyList(),
    val sourceClasses: List<PsiClass> = emptyList(),
    val settings: Settings = Settings(),
    val exportFormat: ExportFormat = ExportFormat.MARKDOWN,
    val outputConfig: OutputConfig = OutputConfig(),
    val actionContext: ActionContext? = null,
    val indicator: ProgressIndicator? = null
) {
    /**
     * Whether the user has selected specific endpoints to export.
     */
    val hasSelection: Boolean
        get() = selectedEndpoints.isNotEmpty()

    /**
     * The endpoints to export (selected or all).
     */
    val endpointsToExport: List<ApiEndpoint>
        get() = if (hasSelection) selectedEndpoints else endpoints

    /**
     * Creates a new context with the specified selected endpoints.
     */
    fun withSelectedEndpoints(endpoints: List<ApiEndpoint>): ExportContext {
        return copy(selectedEndpoints = endpoints)
    }

    /**
     * Creates a new context with the specified export format.
     */
    fun withExportFormat(format: ExportFormat): ExportContext {
        return copy(exportFormat = format)
    }

    /**
     * Creates a new context with the specified output configuration.
     */
    fun withOutputConfig(config: OutputConfig): ExportContext {
        return copy(outputConfig = config)
    }
}

/**
 * Supported export formats.
 */
enum class ExportFormat(
    /**
     * Whether this format supports HTTP endpoints.
     */
    val supportsHttp: Boolean = true,

    /**
     * Whether this format supports gRPC endpoints.
     * Postman does not support gRPC exports.
     */
    val supportsGrpc: Boolean,

    /**
     * The display name for UI purposes.
     */
    val displayName: String
) {
    MARKDOWN(supportsGrpc = true, displayName = "Markdown"),
    YAPI(supportsGrpc = false, displayName = "Yapi"),
    POSTMAN(supportsGrpc = false, displayName = "Postman"),
    CURL(supportsGrpc = true, displayName = "cURL"),
    HTTP_CLIENT(supportsGrpc = true, displayName = "HTTP Client"), ;

    /**
     * Checks if this format is available for the given endpoints.
     * A format is available if it supports at least one of the endpoint types.
     */
    fun isAvailableFor(endpoints: List<ApiEndpoint>): Boolean {
        if (endpoints.isEmpty()) return true
        val hasGrpc = endpoints.any { it.isGrpc }
        val hasHttp = endpoints.any { it.isHttp }
        return (hasGrpc && supportsGrpc) || (hasHttp && supportsHttp)
    }
}

/**
 * Configuration for export output.
 *
 * @param outputDir The output directory
 * @param fileName The output file name
 * @param host The API host URL
 * @param yapiOptions YAPI-specific options
 * @param postmanOptions Postman-specific options
 */
data class OutputConfig(
    val outputDir: String? = null,
    val fileName: String? = null,
    val host: String? = null,
    val yapiOptions: YapiExportOptions? = null,
    val postmanOptions: PostmanExportOptions? = null
) {
    companion object {
        /**
         * Default output configuration with no options set.
         */
        val DEFAULT = OutputConfig()
    }
}

/**
 * YAPI-specific export options.
 *
 * @param selectedToken The selected YAPI project token
 * @param useCustomProject Whether to use a custom project
 */
data class YapiExportOptions(
    val selectedToken: String? = null,
    val useCustomProject: Boolean = false
)

/**
 * Postman-specific export options.
 *
 * @param selectedWorkspaceId The selected workspace ID
 * @param selectedWorkspaceName The selected workspace name
 * @param selectedCollectionId The selected collection ID
 * @param selectedCollectionName The selected collection name
 * @param useCustomCollection Whether to use a custom collection
 */
data class PostmanExportOptions(
    val selectedWorkspaceId: String? = null,
    val selectedWorkspaceName: String? = null,
    val selectedCollectionId: String? = null,
    val selectedCollectionName: String? = null,
    val useCustomCollection: Boolean = false
)
