package com.itangcent.easyapi.core.export

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.channel.spi.ChannelConfig

/**
 * Context object passed to [com.itangcent.easyapi.channel.spi.Channel.export]
 * containing all information needed to perform an export.
 *
 * Channels read settings via `project.settings<T>()` rather than
 * from a cached `settings` field, so settings always reflect the latest values.
 *
 * @property project the IntelliJ project
 * @property endpoints all discovered API endpoints
 * @property selectedEndpoints user-selected subset of endpoints (empty if none selected)
 * @property sourceClasses the PSI classes that were scanned
 * @property channelId the target channel ID (e.g. "markdown", "postman")
 * @property channelConfig channel-specific configuration
 * @property indicator optional progress indicator for reporting progress
 * @property headless true when nobody can answer a dialog: an external caller such as an MCP client
 *   started the export. The channel then shows no UI and never waits for the EDT; what a dialog would
 *   ask comes from [channelConfig] or settings, and a missing value fails with a message saying how to
 *   supply it. Only channels with [com.itangcent.easyapi.channel.spi.Channel.supportsHeadless] receive it
 * @property itemListener receives one [ExportItemResult] per endpoint while the channel exports, except
 *   from an export that fails as a whole (no server configured, a passed token that fails its check with
 *   the server), which may report none and return [ExportResult.Error]; `null` when nobody asked
 */
data class ExportContext(
    val project: Project,
    val endpoints: List<ApiEndpoint>,
    val selectedEndpoints: List<ApiEndpoint> = emptyList(),
    val sourceClasses: List<PsiClass> = emptyList(),
    val channelId: String = "markdown",
    val channelConfig: ChannelConfig = ChannelConfig.Empty,
    val indicator: ProgressIndicator? = null,
    val headless: Boolean = false,
    val itemListener: ExportItemListener? = null
) {
    /** Whether the user has selected specific endpoints to export. */
    val hasSelection: Boolean
        get() = selectedEndpoints.isNotEmpty()

    /** The endpoints to export: selected subset if available, otherwise all. */
    val endpointsToExport: List<ApiEndpoint>
        get() = if (hasSelection) selectedEndpoints else endpoints

    /** Returns a copy with the given selected endpoints. */
    fun withSelectedEndpoints(endpoints: List<ApiEndpoint>): ExportContext {
        return copy(selectedEndpoints = endpoints)
    }

    /** Returns a copy with the given channel ID and configuration. */
    fun withChannel(id: String, config: ChannelConfig = ChannelConfig.Empty): ExportContext {
        return copy(channelId = id, channelConfig = config)
    }
}
