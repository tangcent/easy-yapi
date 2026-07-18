package com.itangcent.easyapi.exporter.channel

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ExportContext
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.exporter.model.isGrpc
import com.itangcent.easyapi.exporter.model.isHttp
import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.settings.ui.SettingsPanel
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
 * - Optionally set [exposeAsAction] and [actionText] to add a top-level action menu entry.
 * - Optionally set [settingsType] and [settingsTabOrder] for settings-tab wiring/ordering.
 *
 * @see ChannelRegistry for the registry that discovers and filters channels
 * @see ChannelConfig for channel-specific configuration
 */
interface Channel {

    /** Unique identifier for this channel (e.g. "markdown", "postman", "curl"). */
    val id: String

    /** Human-readable name shown in UI (e.g. "Markdown", "Postman", "cURL"). */
    val displayName: String

    /** Whether this channel supports HTTP/REST endpoints. Defaults to `true`. */
    val supportsHttp: Boolean get() = true

    /** Whether this channel supports gRPC endpoints. Defaults to `false`. */
    val supportsGrpc: Boolean get() = false

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

    /**
     * Creates the persistent settings panel (the dedicated tab in Settings).
     * Return `null` if the channel has no settings UI.
     *
     * Channel panels are self-contained: they read/write their own modules
     * via [com.itangcent.easyapi.settings.SettingBinder] internally, so the
     * return type uses star projection ([SettingsPanel<*>?]).
     */
    fun createSettingsPanel(project: Project): SettingsPanel<*>? = null

    /**
     * Config-file names this channel contributes to the extension-config
     * fallback list (e.g. `"yapi"`, `"hoppscotch"`).
     */
    fun configFiles(): List<String> = emptyList()

    /**
     * Channel-specific [RuleKey]s contributed by this channel (e.g.
     * Hoppscotch's `hopp.prerequest`, YApi's `yapi.project`).
     *
     * Keys already declared in [com.itangcent.easyapi.rule.RuleKeys] (the
     * shared general set) must NOT be repeated here — return only the keys
     * that live in this channel's own package. Returns an empty list by
     * default for channels with no additional rule keys.
     *
     * Consumed by [com.itangcent.easyapi.ai.tools.ListRuleKeysTool] so the
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
