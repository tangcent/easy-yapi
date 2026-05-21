package com.itangcent.easyapi.exporter.channel

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ExportContext
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.exporter.model.isGrpc
import com.itangcent.easyapi.exporter.model.isHttp

/**
 * Extension point interface for API export channels (output destinations).
 *
 * Implementations are registered via the `com.itangcent.idea.plugin.easy-yapi.apiChannel`
 * extension point in `plugin.xml`. Unlike [ClassExporter], channels are application-scoped
 * (no `area` attribute), so IntelliJ creates a single shared instance with a no-arg constructor.
 * Project context is passed through method parameters instead.
 *
 * ## Implementing
 *
 * - Constructor must be no-arg (required by the application-scoped extension point).
 * - Override [id] and [displayName] for identification.
 * - Override [export] to perform the actual export.
 * - Optionally override [createOptionsPanel] to provide channel-specific configuration UI.
 * - Optionally override [handleResult] to process the export result (e.g., show in browser).
 * - Optionally set [exposeAsAction] and [actionText] to add a top-level action menu entry.
 *
 * @see ApiChannelRegistry for the registry that discovers and filters channels
 * @see ChannelConfig for channel-specific configuration
 */
interface ApiChannel {

    /** Unique identifier for this channel (e.g. "markdown", "postman", "curl"). */
    val id: String

    /** Human-readable name shown in UI (e.g. "Markdown", "Postman", "cURL"). */
    val displayName: String

    /** Whether this channel supports HTTP/REST endpoints. Defaults to `true`. */
    val supportsHttp: Boolean get() = true

    /** Whether this channel supports gRPC endpoints. Defaults to `false`. */
    val supportsGrpc: Boolean get() = false

    /**
     * Checks whether this channel can handle the given endpoints.
     *
     * Returns `true` if at least one endpoint type (HTTP or gRPC) is supported
     * by this channel and present in the list.
     */
    fun isAvailableFor(endpoints: List<ApiEndpoint>): Boolean {
        if (endpoints.isEmpty()) return true
        val hasGrpc = endpoints.any { it.isGrpc }
        val hasHttp = endpoints.any { it.isHttp }
        if (!supportsHttp && !supportsGrpc) return false
        return (hasGrpc && supportsGrpc) || (hasHttp && supportsHttp)
    }

    /**
     * Creates a channel-specific options panel for configuration.
     *
     * @param project the current IntelliJ project
     * @return the options panel, or `null` if no configuration is needed
     */
    fun createOptionsPanel(project: Project): ChannelOptionsPanel?

    /**
     * Performs the export operation.
     *
     * @param context the export context containing endpoints, project, and config
     * @return the export result
     */
    suspend fun export(context: ExportContext): ExportResult

    /**
     * Handles a successful export result (e.g., opens in browser, copies to clipboard).
     *
     * @param project the current IntelliJ project
     * @param result the successful export result
     * @param config the channel configuration used
     * @return `true` if the result was handled (prevents default handling)
     */
    suspend fun handleResult(
        project: Project,
        result: ExportResult.Success,
        config: ChannelConfig
    ): Boolean = false

    /** Whether this channel should be exposed as a top-level action in the IDE. */
    val exposeAsAction: Boolean get() = false

    /** Text to display in the action menu when [exposeAsAction] is `true`. */
    val actionText: String? get() = null
}
