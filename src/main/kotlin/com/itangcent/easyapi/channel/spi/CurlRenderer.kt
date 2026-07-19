package com.itangcent.easyapi.channel.spi

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.script.ScriptScope

/**
 * SPI for cURL rendering operations needed by `core.*` callers.
 *
 * Decisions CO3 + CO8: `core.rule.context.ScriptApiEndpoint.toCurl` and
 * `core.dashboard.ApiDashboardPanel` (copy-as-cURL action, scope resolution)
 * MUST NOT import concrete `channel.curl.*` types. They go through this SPI
 * instead; the implementation lives in `channel.curl.CurlRendererService` and
 * is registered in `plugin.xml` as an `<applicationService>` (Decision CO8).
 *
 * ## Why application-scoped (not project-scoped)
 *
 * `ScriptApiEndpoint.toCurl` may be constructed with `project = null` (unit
 * tests, headless rule eval). A project-scoped service would be unreachable
 * in that case. Application-scoped lookup (`ApplicationManager.getApplication()
 * .service<CurlRenderer>()`) works with or without a project; project-bound
 * methods take `Project` as a parameter and delegate to the appropriate
 * project-scoped services (`CurlExportResolver`, `SettingBinder`).
 *
 * ## Method grouping
 *
 * - [format] — pure format path; no `Project` needed (headless-safe,
 *   EDT-free, no scripts). Used by `ScriptApiEndpoint.toCurl` when `project`
 *   is null.
 * - [buildSync] — full pipeline (settings → optional pre-scripts → format).
 *   Used by `ScriptApiEndpoint.toCurl` when `project` is non-null.
 * - [formatForCopy] — single-endpoint "Copy as cURL" pipeline (resolve →
 *   optional pre-scripts → format). Used by `ApiDashboardPanel`.
 * - [copyFromEdited] — reads the persisted `copyFromEdited` setting for the
 *   Dashboard's "use edited endpoint" decision.
 * - [resolveFolderAndClassScopes] — folder + class scope resolution for the
 *   Dashboard's script editor.
 *
 * ## Threading
 *
 * - [format], [buildSync], [copyFromEdited], [resolveFolderAndClassScopes] —
 *   non-suspend; safe from background threads. [buildSync] internally
 *   `runBlocking`s the suspend `CurlBuilder.build` (safe because
 *   `PreScriptApplier.applyScripts` is EDT-free).
 * - [formatForCopy] — `suspend`; caller must be on a background coroutine
 *   (it may show an environment chooser on EDT via `swing`).
 */
interface CurlRenderer {

    /**
     * Default placeholder host used when no host is supplied.
     *
     * Mirrors `CurlBuilder.DEFAULT_HOST` (`"{{host}}"`). Exposed as a SPI
     * constant so `core.rule.context.ScriptApiEndpoint.toCurl` can default
     * its `host` parameter without importing `channel.curl.CurlBuilder`.
     */
    val defaultHost: String

    /**
     * Pure format path — NO `Project`, NO `suspend`, NO scripts.
     *
     * Equivalent to `CurlBuilder.format(endpoint, host)` with default
     * `CurlFormatOptions`. Used by `ScriptApiEndpoint.toCurl` when `project`
     * is null (headless rule eval, unit tests).
     *
     * @param endpoint The endpoint to format.
     * @param host Target host; defaults to [defaultHost] when blank.
     * @return The formatted cURL command string.
     */
    fun format(endpoint: ApiEndpoint, host: String): String

    /**
     * Full pipeline: optional pre-script execution → format.
     *
     * Non-suspend wrapper around `CurlBuilder.buildSync`. Reads the user's
     * persisted `CurlSettings` format flags from [project]; when
     * [runPreScripts] is true, resolves folder + class scopes and applies
     * pre-request scripts to a deep copy before formatting.
     *
     * Safe to call from background threads (rule-script thread). NOT safe
     * on EDT — `runBlocking` would block the UI.
     *
     * @param project Required for settings + scripts; pass null to force the
     *   pure-format path (same as [format]).
     * @param endpoint The endpoint to build a cURL command for.
     * @param host Target host; defaults to [defaultHost] when blank.
     * @param runPreScripts When true and [project] is set, run folder+class
     *   pre-request scripts before formatting.
     * @return The formatted cURL command.
     */
    fun buildSync(
        project: Project?,
        endpoint: ApiEndpoint,
        host: String,
        runPreScripts: Boolean,
    ): String

    /**
     * Single-endpoint "Copy as cURL" pipeline: resolve variables → optional
     * pre-scripts → format.
     *
     * Delegates to `CurlExportResolver.formatForCopy`. The caller remains
     * responsible for the surrounding UI concerns (host selection,
     * edited-vs-original decision, clipboard write, notification).
     *
     * @param project The current IntelliJ project.
     * @param endpoint The endpoint to render (caller decides edited vs original).
     * @param host The target host (already resolved by the caller).
     * @return the formatted cURL command, or `null` if the user cancelled the
     *         environment selection prompt.
     */
    suspend fun formatForCopy(project: Project, endpoint: ApiEndpoint, host: String): String?

    /**
     * Reads the persisted `copyFromEdited` setting for the Dashboard's
     * "Copy as cURL" action.
     *
     * When true, the action uses the endpoint with dashboard edits applied
     * (path/headers/params/body from the UI fields). When false (default),
     * the original source-code endpoint is used.
     *
     * @param project The current IntelliJ project.
     * @return the current `copyFromEdited` flag value.
     */
    fun copyFromEdited(project: Project): Boolean

    /**
     * Resolves folder + class scopes for [endpoint] (outer → inner order,
     * matching `RequestExecutor.resolveScripts`).
     *
     * Used by the Dashboard's `ApiDashboardPanel.resolveScriptScopesForEndpoint`
     * for the script editor card. Folder + class only — endpoint-scope is
     * intentionally NOT included here (the script editor operates at folder
     * or class level, not per-endpoint).
     *
     * @param endpoint The endpoint whose containing folder + class scopes to resolve.
     * @return the resolved scopes (folder first, class second); empty if neither is available.
     */
    fun resolveFolderAndClassScopes(endpoint: ApiEndpoint): List<ScriptScope>

    companion object {
        /**
         * Default placeholder host constant.
         *
         * Exposed as a `const val` so it can be used as a Kotlin default
         * parameter value (e.g. `fun toCurl(host: String = CurlRenderer.DEFAULT_HOST)`).
         */
        const val DEFAULT_HOST = "{{host}}"

        /**
         * Gets the application-scoped `CurlRenderer` instance.
         *
         * Works with or without a `Project` — use this for the pure-format
         * path or when the caller doesn't have a project. Project-bound
         * methods take `Project` as a parameter.
         */
        fun getInstance(): CurlRenderer = ApplicationManager.getApplication().service()
    }
}
