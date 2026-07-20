package com.itangcent.easyapi.channel.spi

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.internal.PluginInfo
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.read

/**
 * Project-level service that discovers [Channel] implementations
 * via the `channel` extension point.
 *
 * Collapses the former `ApiChannelRegistry` + `ChannelSettingsRegistry` into
 * a single registry. The EP is project-scoped (`area="IDEA_PROJECT"`), so
 * extensions are read from the [project]'s extension area, not the
 * application root area.
 *
 * ## Channel enablement
 *
 * [isEnabled] is the single chokepoint for resolving a channel's effective
 * enabled state (overlaying the stored user preference on [Channel.enabledByDefault]).
 * The enumeration query methods ([getAvailableChannels], [getActionChannels],
 * [channelsForSettings]) all filter by [isEnabled], so a disabled channel is
 * hidden from every consumer. The registry primitives [allChannels] and
 * [getChannel] remain **unfiltered** (pure "what is registered" lookups) —
 * the export boundary ([com.itangcent.easyapi.core.export.ExportOrchestrator])
 * is the single place that refuses a disabled channel resolved by id.
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
class ChannelRegistry(private val project: Project) : IdeaLog {

    companion object {
        fun getInstance(project: Project): ChannelRegistry = project.service()

        // IV: this EP-name string differs between easy-api and easy-yapi
        private val EP = ExtensionPointName.create<Channel>("${PluginInfo.PLUGIN_ID}.channel")

        /**
         * Pure resolution rule for a channel's effective enabled state, extracted
         * so unit tests can exercise the full truth table without a [Project].
         *
         * `true` if [channel]'s id is explicitly in [enabledIds] (explicit-on wins),
         * OR [channel] is [Channel.enabledByDefault] and its id is not explicitly
         * in [disabledIds]. Absence in both arrays falls back to
         * [Channel.enabledByDefault].
         */
        internal fun resolveEnabled(
            channel: Channel,
            enabledIds: Array<String>,
            disabledIds: Array<String>
        ): Boolean =
            channel.id in enabledIds ||
                (channel.enabledByDefault && channel.id !in disabledIds)
    }

    /** All registered [Channel] implementations, or an empty list if
     *  the `channel` extension point is not registered in the current
     *  project area (e.g. in lightweight unit tests that do not load `plugin.xml`).
     *
     *  Unfiltered by design — returns every registered channel regardless of
     *  enabled state (Req 4.5). Consumers that need only enabled channels must
     *  use [getAvailableChannels], [getActionChannels], or [channelsForSettings]. */
    fun allChannels(): List<Channel> = extensionListSafe()

    /** Finds a channel by its unique [id], or `null` if not found.
     *
     *  Unfiltered by design — returns the channel regardless of enabled state
     *  (Req 4.5, design Decision 3). The export boundary refuses disabled
     *  channels; this lookup stays a pure registry primitive. */
    fun getChannel(id: String): Channel? =
        allChannels().firstOrNull { it.id == id }

    /**
     * Resolves the effective enabled state of [channel]: `true` if the user has
     * explicitly enabled it (its id is in `GeneralSettings.enabledChannels`), OR
     * it is [Channel.enabledByDefault] and not explicitly disabled (its id is
     * not in `GeneralSettings.disabledChannels`). Reads the stored preference
     * via [SettingBinder] (cached ~10s by
     * [com.itangcent.easyapi.core.settings.DefaultSettingBinder]).
     *
     * If the settings storage cannot be read, falls back to
     * [Channel.enabledByDefault] for every channel and does not throw (Req 2.6).
     */
    fun isEnabled(channel: Channel): Boolean {
        val settings = try {
            SettingBinder.getInstance(project).read<GeneralSettings>()
        } catch (e: Exception) {
            LOG.warn("Failed to read channel enablement settings, falling back to enabledByDefault", e)
            return channel.enabledByDefault
        }
        return resolveEnabled(channel, settings.enabledChannels, settings.disabledChannels)
    }

    /** Returns channels that can handle the given [endpoints] AND are enabled. */
    fun getAvailableChannels(endpoints: List<ApiEndpoint>): List<Channel> =
        allChannels().filter { it.isAvailableFor(endpoints) && isEnabled(it) }

    /** Returns channels that should be exposed as top-level IDE actions AND are enabled. */
    fun getActionChannels(): List<Channel> =
        allChannels().filter { it.exposeAsAction && isEnabled(it) }

    /** Aggregated config-file names from all channels. */
    fun configFiles(): List<String> =
        allChannels().flatMap { it.configFiles() }

    /** Channels eligible for a Settings tab AND enabled, sorted by [Channel.settingsTabOrder]. */
    fun channelsForSettings(): List<Channel> =
        allChannels().filter { isEnabled(it) }.sortedBy { it.settingsTabOrder }

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
