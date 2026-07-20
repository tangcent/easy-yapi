package com.itangcent.easyapi.format.spi

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.internal.PluginInfo
import com.itangcent.easyapi.core.internal.PluginInfo.PLUGIN_ID
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.read

/**
 * Project-level service that discovers [FieldFormatChannel] implementations via
 * the application-scoped `fieldFormatChannel` extension point and resolves their
 * effective enabled state.
 *
 * Mirrors [com.itangcent.easyapi.channel.spi.ChannelRegistry]: it is the
 * single chokepoint for "is this format enabled?" consulted by both the action
 * group ([FieldFormatAction.update] / [FieldFormatActionGroup.refreshActions])
 * and the Settings UI section ("Field Format Channels" in `GeneralSettingsPanel`).
 *
 * ## EP scope difference from `ChannelRegistry`
 *
 * The `fieldFormatChannel` EP is **application-scoped** (no `area` attribute in
 * `plugin.xml`), unlike the `channel` EP which is project-scoped
 * (`area="IDEA_PROJECT"`). Therefore [allChannels] reads the EP directly via
 * [ExtensionPointName.extensionList] — no `project.extensionArea` lookup is
 * needed. The registry is still a **project** `@Service` because
 * [SettingBinder.getInstance] requires a [Project] to read the
 * APPLICATION-scoped [GeneralSettings] (mirroring how `ChannelRegistry` reads
 * the same settings). Query methods take no project parameter — the registry
 * holds the project internally (Decision A1).
 *
 * ## Usage
 *
 * ```kotlin
 * val channels = FieldFormatChannelRegistry.getInstance(project).allChannels()
 * val enabled = FieldFormatChannelRegistry.getInstance(project).getEnabledChannels()
 * val isOn = FieldFormatChannelRegistry.getInstance(project).isEnabled(channel)
 * ```
 *
 * @see FieldFormatChannel
 */
@Service(Service.Level.PROJECT)
class FieldFormatChannelRegistry(private val project: Project) : IdeaLog {

    companion object {
        fun getInstance(project: Project): FieldFormatChannelRegistry = project.service()

        // IV: this EP-name string differs between easy-api and easy-yapi
        private val EP = ExtensionPointName.create<FieldFormatChannel>(
            "$PLUGIN_ID.fieldFormatChannel"
        )

        /**
         * Pure resolution rule for a format's effective enabled state, extracted
         * so unit tests can exercise the full truth table without a [Project].
         *
         * `true` if [channel]'s id is explicitly in [enabledIds] (explicit-on wins),
         * OR [channel] is [FieldFormatChannel.enabledByDefault] and its id is not
         * explicitly in [disabledIds]. Absence in both arrays falls back to
         * [FieldFormatChannel.enabledByDefault].
         *
         * Identical to [com.itangcent.easyapi.channel.spi.ChannelRegistry.resolveEnabled],
         * applied to field-format ids.
         */
        internal fun resolveEnabled(
            channel: FieldFormatChannel,
            enabledIds: Array<String>,
            disabledIds: Array<String>
        ): Boolean =
            channel.id in enabledIds ||
                    (channel.enabledByDefault && channel.id !in disabledIds)
    }

    /**
     * All registered [FieldFormatChannel] implementations, or an empty list if
     * the `fieldFormatChannel` extension point is not registered (e.g. in
     * lightweight unit tests that do not load `plugin.xml`).
     *
     * The EP is application-scoped, so it is read directly via
     * [ExtensionPointName.extensionList] — no project extension-area lookup is
     * needed (unlike `ChannelRegistry` which uses `project.extensionArea` for
     * the project-scoped `channel` EP).
     *
     * Unfiltered by design — returns every registered format regardless of
     * enabled state (Req A3.4 — so a disabled format can be re-enabled in the
     * Settings UI). Consumers that need only enabled formats must use
     * [getEnabledChannels].
     */
    fun allChannels(): List<FieldFormatChannel> =
        runCatching { EP.extensionList }.getOrDefault(emptyList())

    /**
     * Resolves the effective enabled state of [channel]: `true` if the user has
     * explicitly enabled it (its id is in
     * [GeneralSettings.enabledFieldFormatChannels]), OR it is
     * [FieldFormatChannel.enabledByDefault] and not explicitly disabled (its id
     * is not in [GeneralSettings.disabledFieldFormatChannels]). Reads the stored
     * preference via [SettingBinder] (cached ~10s by
     * [com.itangcent.easyapi.core.settings.DefaultSettingBinder]).
     *
     * If the settings storage cannot be read, falls back to
     * [FieldFormatChannel.enabledByDefault] for every format and does not throw
     * (Req A2.6; AGENTS.md forbids silent `runCatching`).
     */
    fun isEnabled(channel: FieldFormatChannel): Boolean {
        val settings = try {
            SettingBinder.getInstance(project).read<GeneralSettings>()
        } catch (e: Exception) {
            LOG.warn(
                "Failed to read field-format enablement settings, falling back to enabledByDefault",
                e
            )
            return channel.enabledByDefault
        }
        return resolveEnabled(
            channel,
            settings.enabledFieldFormatChannels,
            settings.disabledFieldFormatChannels
        )
    }

    /**
     * Returns all registered formats that are enabled (filtered by [isEnabled]).
     * Consumed by [FieldFormatActionGroup.ensureActionsRegistered] so disabled
     * formats do not get an action registered.
     */
    fun getEnabledChannels(): List<FieldFormatChannel> =
        allChannels().filter { isEnabled(it) }
}
