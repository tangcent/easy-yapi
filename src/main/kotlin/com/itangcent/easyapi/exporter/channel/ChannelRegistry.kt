package com.itangcent.easyapi.exporter.channel

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.exporter.model.ApiEndpoint

/**
 * Project-level service that discovers [Channel] implementations
 * via the `channel` extension point.
 *
 * Collapses the former `ApiChannelRegistry` + `ChannelSettingsRegistry` into
 * a single registry. The EP is project-scoped (`area="IDEA_PROJECT"`), so
 * extensions are read from the [project]'s extension area, not the
 * application root area.
 *
 * ## Usage
 *
 * ```kotlin
 * val channels = ChannelRegistry.getInstance(project).allChannels()
 * val channel = ChannelRegistry.getInstance(project).getChannel("markdown")
 * val settingsChannels = ChannelRegistry.getInstance(project).channelsForSettings()
 * ```
 *
 * @see Channel
 */
@Service(Service.Level.PROJECT)
class ChannelRegistry(private val project: Project) {

    companion object {
        fun getInstance(project: Project): ChannelRegistry = project.service()

        // IV: this EP-name string differs between easy-api and easy-yapi
        private val EP = ExtensionPointName.create<Channel>("com.itangcent.idea.plugin.easy-yapi.channel")
    }

    /** All registered [Channel] implementations, or an empty list if
     *  the `channel` extension point is not registered in the current
     *  project area (e.g. in lightweight unit tests that do not load `plugin.xml`). */
    fun allChannels(): List<Channel> = extensionListSafe()

    /** Finds a channel by its unique [id], or `null` if not found. */
    fun getChannel(id: String): Channel? =
        allChannels().firstOrNull { it.id == id }

    /** Returns channels that can handle the given [endpoints]. */
    fun getAvailableChannels(endpoints: List<ApiEndpoint>): List<Channel> =
        allChannels().filter { it.isAvailableFor(endpoints) }

    /** Returns channels that should be exposed as top-level IDE actions. */
    fun getActionChannels(): List<Channel> =
        allChannels().filter { it.exposeAsAction }

    /** Aggregated config-file names from all channels. */
    fun configFiles(): List<String> =
        allChannels().flatMap { it.configFiles() }

    /** Channels sorted by [Channel.settingsTabOrder] for UI tab placement. */
    fun channelsForSettings(): List<Channel> =
        allChannels().sortedBy { it.settingsTabOrder }

    /**
     * Reads the EP defensively from the [project]'s extension area.
     * `project.extensionArea.getExtensionPoint(...)` throws
     * `IllegalArgumentException` ("Missing extension point") when the EP is not
     * registered — which happens in lightweight unit tests that run without
     * `plugin.xml`. We treat a missing EP the same as "no extensions".
     */
    private fun extensionListSafe(): List<Channel> =
        runCatching {
            @Suppress("UNCHECKED_CAST")
            project.extensionArea
                .getExtensionPoint<Channel>(EP.name)
                .extensionList
        }.getOrDefault(emptyList())
}
