package com.itangcent.easyapi.exporter.model

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.exporter.channel.ChannelConfig

/**
 * Context object passed to [com.itangcent.easyapi.exporter.channel.Channel.export]
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
 */
data class ExportContext(
    val project: Project,
    val endpoints: List<ApiEndpoint>,
    val selectedEndpoints: List<ApiEndpoint> = emptyList(),
    val sourceClasses: List<PsiClass> = emptyList(),
    val channelId: String = "markdown",
    val channelConfig: ChannelConfig = ChannelConfig.Empty,
    val indicator: ProgressIndicator? = null
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
