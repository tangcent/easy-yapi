package com.itangcent.easyapi.channel.curl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.internal.threading.swing
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.logging.IdeaConsoleProvider
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.script.env.Environment
import com.itangcent.easyapi.core.script.env.EnvironmentService
import com.itangcent.easyapi.core.settings.settings

/**
 * Bridges persistent [CurlSettings] (render mode) + live environment/config sources
 * to the pure [EndpointVariableResolver]. Used by both [CurlChannel] (batch export)
 * and the Dashboard "Copy as cURL" action (single endpoint), so the env-service /
 * ConfigReader / EDT-prompt wiring lives in exactly one place.
 *
 * Following the [com.itangcent.easyapi.channel.yapi.DefaultUpdateConfirmation]
 * pattern: a project-scoped service that reads the export mode from settings internally
 * and caches an "apply-to-all" decision across the batch. Callers no longer pass
 * `renderMode` — the service reads it from [CurlSettings] on each call, so settings
 * changes take effect immediately without re-constructing the resolver.
 *
 * Threading: all entry points are `suspend`. `ALWAYS_ASK` shows the environment
 * chooser on EDT via [swing]; the caller is expected to be on a background coroutine
 * (e.g. [com.itangcent.easyapi.core.internal.threading.backgroundAsync] / the export pipeline).
 *
 * Cancel semantics: if the user closes the environment dialog (Esc / X), `resolve`
 * and `resolveAll` return `null` — callers should abort silently. "Skip (keep
 * placeholders)" is distinct: it proceeds with an empty variable map, so placeholders
 * stay unreplaced (only the [ConfigReader] fallback is consulted). In `resolveAll`,
 * the skip decision is cached for the remainder of the batch (like
 * [DefaultUpdateConfirmation.applyAllDecision]).
 */
@Service(Service.Level.PROJECT)
class CurlExportResolver(
    private val project: Project,
) : IdeaLog {

    /** Cached user decision for the current batch (resolveAll). Null = not yet decided. */
    private var applyAllEnv: Environment? = null
    private var applyAllSkip: Boolean = false

    /**
     * Resolves [endpoint] + [host] for a single cURL command (Copy-as-cURL path).
     *
     * Reads [CurlRenderMode] from [CurlSettings] directly — callers no longer pass it.
     *
     * @return the resolved pair, or `null` if the user cancelled the environment prompt.
     */
    suspend fun resolve(
        endpoint: ApiEndpoint,
        host: String,
    ): Pair<ApiEndpoint, String>? {
        val renderMode = project.settings<CurlSettings>().renderModeEnum()
        if (renderMode == CurlRenderMode.NEVER_RENDER) return endpoint to host

        val envMap = resolveEnvMap(renderMode, isBatch = false) ?: return null
        val fallback = fallback()
        val console = IdeaConsoleProvider.getInstance(project).getConsole()

        val r = EndpointVariableResolver.resolve(endpoint, host, envMap, fallback)
        if (r.missing.isNotEmpty()) {
            console.warn("curl: unresolved variables in '${endpoint.name}': ${r.missing.joinToString(", ")}")
        }
        return r.endpoint to r.host
    }

    /**
     * Single-endpoint "copy as cURL" pipeline: resolve variables → format with the
     * user's persisted [CurlSettings] format flags.
     *
     * Centralizes the resolve→format sequence that previously lived inline in
     * `ApiDashboardPanel.showPopupMenu`. Format options are read from
     * [CurlSettings.toFormatOptions] so the settings→options mapping has exactly
     * one home (a 6th format flag added to [CurlSettings] flows here for free).
     *
     * Callers remain responsible for the surrounding UI concerns only:
     * - selecting the host (e.g. from the dashboard env dropdown)
     * - the `copyFromEdited` decision (original vs edited endpoint)
     * - clipboard write + success notification
     *
     * Cancel/error handling policy:
     * - [CancellationException] is **not** caught here — it propagates so the
     *   caller can log "cancelled by user" distinctly (matches the pre-refactor
     *   Copy action behavior and [CurlChannel.export]).
     * - Other throwables propagate too; the caller is expected to wrap in a
     *   try/catch that logs and notifies. Keeping this method free of UI
     *   notification code means it stays unit-testable without EDT.
     *
     * @param endpoint The endpoint to render (caller decides edited vs original).
     * @param host The target host (already resolved by the caller).
     * @return the formatted cURL command, or `null` if the user cancelled the
     *         environment selection prompt.
     */
    suspend fun formatForCopy(endpoint: ApiEndpoint, host: String): String? {
        val resolved = resolve(endpoint, host) ?: return null
        val settings = project.settings<CurlSettings>()
        val options = settings.toFormatOptions()

        // When pre-scripts are enabled, run folder+class+endpoint scopes
        // before formatting. The endpoint scope is included here (unlike
        // the batch path) so per-endpoint inline scripts saved via the dashboard UI are
        // consulted for the single-endpoint copy action — mirrors RequestExecutor which
        // appends `input.preRequestScript` after folder+class scopes.
        val endpointToFormat = if (settings.runPreScripts) {
            val scopes = CurlScriptScopes.resolveAllScopes(resolved.first)
            PreScriptApplier.getInstance(project).applyScripts(resolved.first, resolved.second, scopes)
        } else {
            resolved.first
        }
        return CurlFormatter.format(endpointToFormat, resolved.second, options)
    }

    /**
     * Resolves [endpoints] + [host] for a batch cURL export (CurlChannel.export path).
     *
     * Reads [CurlRenderMode] from [CurlSettings] directly — callers no longer pass it.
     *
     * The host is resolved once (from the first endpoint's result) since it is shared
     * across the batch. Each endpoint is still resolved individually so per-endpoint
     * `missing` sets are reported correctly.
     *
     * @return the resolved pair, or `null` if the user cancelled the environment prompt
     *         or the endpoint list is empty AND the user cancelled (empty input short-
     *         circuits to the input unchanged).
     */
    suspend fun resolveAll(
        endpoints: List<ApiEndpoint>,
        host: String,
    ): Pair<List<ApiEndpoint>, String>? {
        val renderMode = project.settings<CurlSettings>().renderModeEnum()
        if (renderMode == CurlRenderMode.NEVER_RENDER) return endpoints to host
        if (endpoints.isEmpty()) return endpoints to host

        val envMap = resolveEnvMap(renderMode, isBatch = true) ?: return null
        val fallback = fallback()
        val console = IdeaConsoleProvider.getInstance(project).getConsole()

        var resolvedHost = host
        val resolvedEndpoints = endpoints.mapIndexed { idx, ep ->
            val r = EndpointVariableResolver.resolve(ep, host, envMap, fallback)
            if (r.missing.isNotEmpty()) {
                console.warn("curl: unresolved variables in '${ep.name}': ${r.missing.joinToString(", ")}")
            }
            if (idx == 0) resolvedHost = r.host
            r.endpoint
        }
        return resolvedEndpoints to resolvedHost
    }

    /**
     * Builds the variable map for [renderMode]. Returns `null` if the user cancelled
     * the ALWAYS_ASK prompt (caller should abort); returns an empty map for Skip or
     * no-active-environment (caller proceeds, placeholders may still resolve via
     * [ConfigReader] fallback).
     *
     * When [isBatch] is true and the user picks an environment, the decision is cached
     * in [applyAllEnv] / [applyAllSkip] so subsequent calls in the same batch skip the
     * prompt (mirrors [DefaultUpdateConfirmation.applyAllDecision]).
     */
    private suspend fun resolveEnvMap(
        renderMode: CurlRenderMode,
        isBatch: Boolean,
    ): Map<String, String>? {
        val envService = EnvironmentService.getInstance(project)
        val console = IdeaConsoleProvider.getInstance(project).getConsole()
        return when (renderMode) {
            CurlRenderMode.ALWAYS_RENDER -> {
                val active = envService.getActiveEnvironment()
                if (active == null) {
                    console.warn("curl: no active environment; ConfigReader fallback only")
                }
                active?.variables ?: emptyMap()
            }
            CurlRenderMode.ALWAYS_ASK -> {
                // Fast path: reuse cached decision within the same batch
                if (isBatch && applyAllSkip) return emptyMap()
                if (isBatch && applyAllEnv != null) return applyAllEnv!!.variables

                val prompt = swing { promptEnvironment(envService) } ?: return null
                when (prompt) {
                    EnvPrompt.Skip -> {
                        if (isBatch) applyAllSkip = true
                        emptyMap()
                    }
                    is EnvPrompt.Selected -> {
                        if (isBatch) applyAllEnv = prompt.environment
                        prompt.environment.variables
                    }
                }
            }
            CurlRenderMode.NEVER_RENDER -> emptyMap()
        }
    }

    private fun fallback(): (String) -> String? =
        { k -> ConfigReader.getInstance(project).getFirst(k) }

    /**
     * Shows the environment chooser on EDT. Returns `null` if the user cancelled
     * (Esc / X / closed window), [EnvPrompt.Skip] if the user chose "Skip", or
     * [EnvPrompt.Selected] wrapping the chosen [Environment].
     *
     * Uses [Messages.showEditableChooseDialog] (non-deprecated) which returns the
     * selected value as a String (or null on cancel) rather than an index.
     */
    private suspend fun promptEnvironment(envService: EnvironmentService): EnvPrompt? {
        val envs = envService.getEnvironments()
        val skipLabel = "Skip (keep placeholders)"
        val labels = listOf(skipLabel) + envs.map { it.name }
        val selected = Messages.showEditableChooseDialog(
            "Select environment for variable rendering:",
            "cURL Export - Environment",
            Messages.getQuestionIcon(),
            labels.toTypedArray(),
            labels.first(),
            null,
        ) ?: return null       // cancelled (Esc / X)
        if (selected == skipLabel) return EnvPrompt.Skip
        val env = envs.firstOrNull { it.name == selected } ?: return EnvPrompt.Skip
        return EnvPrompt.Selected(env)
    }

    /**
     * Resets cached batch decisions. Call between independent export runs.
     * (IntelliJ constructs one service instance per project, so the cache persists
     * across exports — callers should invoke this at the start of a fresh batch.)
     */
    fun resetBatchCache() {
        applyAllEnv = null
        applyAllSkip = false
    }

    /**
     * User's decision when prompted to pick an environment.
     *
     * Mirrors [com.itangcent.easyapi.channel.yapi.UpdateDecision]:
     * sealed type so the caller exhaustively handles cancel vs skip vs selected.
     */
    private sealed interface EnvPrompt {
        /** User chose "Skip (keep placeholders)" — proceed with empty variable map. */
        data object Skip : EnvPrompt
        /** User selected a specific environment. */
        data class Selected(val environment: Environment) : EnvPrompt
    }

    companion object {
        /**
         * Gets the resolver service instance for the given project.
         * Following the [ApiDashboardService.getInstance] pattern.
         */
        fun getInstance(project: Project): CurlExportResolver = project.service()
    }
}
