package com.itangcent.easyapi.channel.spi

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.ExportContext
import com.itangcent.easyapi.core.export.ExportItemResult
import com.itangcent.easyapi.core.export.ExportResult
import com.itangcent.easyapi.core.export.isGrpc
import com.itangcent.easyapi.core.export.isHttp
import com.itangcent.easyapi.core.rule.RuleKey
import com.itangcent.easyapi.core.settings.Settings
import com.itangcent.easyapi.core.settings.ui.SettingsPanel
import com.itangcent.easyapi.core.settings.ui.SettingsPanelProvider
import kotlin.reflect.KClass

/**
 * Extension point interface for API export channels (output destinations).
 *
 * Merges the former `ApiChannel` (export surface) and the surviving
 * `ChannelSettingsExtension` (settings/config-file surface) into a single
 * contract. Channel-specific [ChannelConfig] subtypes are now pluggable
 * (declared in the channel's own package) — `ChannelConfig` is `open`, no
 * longer `sealed`.
 *
 * Implementations are registered via the `channel` extension point
 * (`com.itangcent.idea.plugin.{easy-api|easy-yapi}.channel`) in `plugin.xml`.
 * The EP is project-scoped (`area="IDEA_PROJECT"`), so IntelliJ injects `Project`
 * into each extension's constructor when needed; channel impls in this codebase
 * use a no-arg constructor and receive `Project` via method parameters.
 *
 * ## Implementing
 *
 * - Constructor must be no-arg (required by the extension point).
 * - Override [id] and [displayName] for identification.
 * - Override [export] to perform the actual export.
 * - Optionally override [createOptionsPanel] to provide per-export config UI.
 * - Optionally override [createSettingsPanel] to contribute a persistent settings tab.
 * - Optionally override [configFiles] to contribute rule-config file names.
 * - Optionally override [handleResult] to process the export result.
 * - Optionally set [supportsHeadless] and override [exportOptions] and [buildConfig] to export with no UI.
 * - Optionally set [exposeAsAction] and [actionText] to add a top-level action menu entry.
 * - Optionally set [settingsType] and [settingsTabOrder] for settings-tab wiring/ordering.
 *
 * @see ChannelRegistry for the registry that discovers and filters channels
 * @see ChannelConfig for channel-specific configuration
 */
interface Channel : SettingsPanelProvider {

    /** Unique identifier for this channel (e.g. "markdown", "postman", "curl"). */
    val id: String

    /** Human-readable name shown in UI (e.g. "Markdown", "Postman", "cURL"). */
    val displayName: String

    /** Whether this channel supports HTTP/REST endpoints. Defaults to `true`. */
    val supportsHttp: Boolean get() = true

    /** Whether this channel supports gRPC endpoints. Defaults to `false`. */
    val supportsGrpc: Boolean get() = false

    /**
     * Whether this channel can export with nobody to answer a dialog ([ExportContext.headless]).
     * Defaults to `false`. A channel returning `true` promises, for a headless context:
     * - [export] shows no dialog and never waits for the EDT;
     * - its work is done in [export]: [handleResult] is not called for headless exports;
     * - when [ExportContext.itemListener] is set, [export] reports one [ExportItemResult] per endpoint
     *   of [ExportContext.endpointsToExport], passing that very instance; an export that fails as a whole
     *   (no server configured, a passed token that fails its check with the server) may report none and
     *   return [ExportResult.Error].
     */
    val supportsHeadless: Boolean get() = false

    /** Whether this channel should be exposed as a top-level action in the IDE. */
    val exposeAsAction: Boolean get() = false

    /** Text to display in the action menu when [exposeAsAction] is `true`. */
    val actionText: String? get() = null

    /**
     * Whether this channel is enabled out-of-the-box, before any user preference.
     * Default-on channels appear in all export surfaces immediately; default-off
     * channels (e.g. experimental ones) require the user to enable them in
     * Settings → General → "Export Channels". Resolved against the stored user
     * preference by [ChannelRegistry.isEnabled].
     */
    val enabledByDefault: Boolean get() = true

    /**
     * Whether this channel is in beta status.
     *
     * Beta channels are functional but may have rough edges, incomplete
     * features, or breaking changes in future releases. UI surfaces MAY
     * append a "(Beta)" indicator to the [displayName] and MAY warn the
     * user on first use. Beta channels are typically `enabledByDefault = false`
     * so the user opts in explicitly.
     *
     * Default is `false` (stable). Override to `true` for channels that
     * are not yet production-ready.
     */
    val beta: Boolean get() = false

    /** Hint for settings-tab ordering; lower = earlier. Default 100. */
    val settingsTabOrder: Int get() = 100

    /** Optional: the settings module type this channel owns (for typed panel wiring). */
    val settingsType: KClass<out Settings>? get() = null

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

    /**
     * Creates a channel-specific options panel for per-export configuration.
     *
     * @param project the current IntelliJ project
     * @return the options panel, or `null` if no configuration is needed
     */
    fun createOptionsPanel(project: Project): ChannelOptionsPanel? = null

    /** The options [buildConfig] accepts: the headless counterpart of the [createOptionsPanel] fields. */
    fun exportOptions(): List<ChannelOption> = emptyList()

    /**
     * Builds this export's [ChannelConfig] from [options] with no UI: the headless counterpart of
     * [ChannelOptionsPanel.buildConfig]. [options] holds only names declared by [exportOptions],
     * each value trimmed and non-blank.
     */
    fun buildConfig(options: Map<String, String>): ChannelConfig = ChannelConfig.Empty

    /**
     * Creates the persistent settings panel (the dedicated tab in Settings).
     * Return `null` if the channel has no settings UI.
     *
     * Channel panels are self-contained: they read/write their own modules
     * via [com.itangcent.easyapi.core.settings.SettingBinder] internally, so the
     * return type uses star projection ([SettingsPanel<*>?]).
     */
    override fun createSettingsPanel(project: Project): SettingsPanel<*>? = null

    /**
     * Config-file names this channel contributes to the extension-config
     * fallback list (e.g. `"yapi"`, `"hoppscotch"`).
     */
    fun configFiles(): List<String> = emptyList()

    /**
     * Channel-specific [RuleKey]s contributed by this channel (e.g.
     * Hoppscotch's `hopp.prerequest`, YApi's `yapi.project`).
     *
     * Keys already declared in [com.itangcent.easyapi.core.rule.RuleKeys] (the
     * shared general set) must NOT be repeated here — return only the keys
     * that live in this channel's own package. Returns an empty list by
     * default for channels with no additional rule keys.
     *
     * Consumed by [com.itangcent.easyapi.core.ai.tools.ListRuleKeysTool] so the
     * AI agent sees a complete picture of configurable rule keys across the
     * general set plus every registered channel.
     */
    fun ruleKeys(): List<RuleKey<*>> = emptyList()

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
}
