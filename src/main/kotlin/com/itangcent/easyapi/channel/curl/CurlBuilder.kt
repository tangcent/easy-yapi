package com.itangcent.easyapi.channel.curl

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.export.ApiEndpoint

/**
 * General, reusable "build a cURL command from an endpoint" entry point.
 *
 * Usable from:
 * - The cURL export channel ([CurlChannel]) and Dashboard "Copy as cURL" (via [CurlExportResolver])
 * - `export.after` rule scripts via [com.itangcent.easyapi.core.rule.context.ScriptApiEndpoint.toCurl]
 * - Markdown templates via [com.itangcent.easyapi.channel.markdown.template.HttpView.curl]
 *
 * ## Purity contract
 *
 * The [format] step is **pure** with respect to `Project`/EDT — no `Project`,
 * no `suspend`, no coroutine suspension. It delegates to [CurlFormatter] and is
 * unit-testable with plain JUnit. This is the entry point Markdown's
 * `HttpView.curl()` and rule `api.toCurl()` (with `runPreScripts=false`) use.
 *
 * The [build] step is `suspend` because the optional pre-script application
 * requires a `Project` for [ScriptCacheService] / [PmScriptExecutor]. When
 * [CurlBuildOptions.runPreScripts] is `false` (or [CurlBuildOptions.scopes] is
 * empty, or `project` is null), [build] early-returns [format] and incurs no
 * `Project` dependency.
 *
 * ## Headless-safe
 *
 * The builder NEVER prompts the user for a host and NEVER shows an environment
 * dialog — it is headless-safe. [DEFAULT_HOST] (`"{{host}}"`) is used when no host
 * is supplied, so output is non-empty and round-trips through variable resolution
 * later (matches the Postman `{{url}}` convention).
 *
 * ## Determinism
 *
 * Output is deterministic for a given `(endpoint, host, options)` — same inputs
 * produce the same string. This holds for [format] unconditionally; for [build]
 * it holds when `runPreScripts=false` (scripts may introduce nondeterminism via
 * `pm.environment.get` lookups, which is expected).
 */
object CurlBuilder {

    /**
     * Default placeholder host when none is supplied.
     *
     * Using the literal `{{host}}` keeps output non-empty and round-trips through
     * [EndpointVariableResolver] / environment rendering later. The builder never
     * prompts and never touches EDT to resolve this — callers resolve it
     * themselves (rules via Groovy + [com.itangcent.easyapi.core.config.ConfigReader];
     * Markdown via the `markdown.curl.host` config key).
     */
    const val DEFAULT_HOST = "{{host}}"

    /**
     * Pure format step — NO `Project`, NO `suspend`, NO scripts.
     *
     * Delegates to [CurlFormatter.format] with [options.format]. If
     * [options.runPreScripts] is `true`, the caller MUST have already applied
     * scripts to [endpoint]; this entry point does not run them. Use [build] for
     * the full pipeline.
     *
     * Unit-testable with plain JUnit.
     *
     * @param endpoint The endpoint to format.
     * @param host Target host; defaults to [DEFAULT_HOST] when blank.
     * @param options Build options; only [CurlBuildOptions.format] is consulted here.
     * @return The formatted cURL command string.
     */
    fun format(
        endpoint: ApiEndpoint,
        host: String = DEFAULT_HOST,
        options: CurlBuildOptions = CurlBuildOptions(),
    ): String {
        val effectiveHost = host.takeIf { it.isNotBlank() } ?: DEFAULT_HOST
        return CurlFormatter.format(endpoint, effectiveHost, options.format)
    }

    /**
     * Full pipeline: optional pre-script execution → format.
     *
     * `suspend` because the script step needs `Project` (for [PreScriptApplier] →
     * [PmScriptExecutor]) and the Groovy engine may do I/O. When
     * [options.runPreScripts] is `false` (or [options.scopes] is empty, or
     * `project` is null), this early-returns [format] and never touches `Project`
     * or suspends meaningfully.
     *
     * @param project Required only when running scripts; ignored otherwise. Pass
     *   `null` for the pure-format path.
     * @param endpoint The endpoint to build a cURL command for.
     * @param host Target host; defaults to [DEFAULT_HOST] when blank.
     * @param options Build options (format flags + script controls).
     * @return The formatted cURL command.
     */
    suspend fun build(
        project: Project?,
        endpoint: ApiEndpoint,
        host: String = DEFAULT_HOST,
        options: CurlBuildOptions = CurlBuildOptions(),
    ): String {
        if (!options.runPreScripts || options.scopes.isEmpty() || project == null) {
            return format(endpoint, host, options)
        }
        val scripted = PreScriptApplier.getInstance(project).applyScripts(endpoint, host, options.scopes)
        return CurlFormatter.format(scripted, host, options.format)
    }

    /**
     * Non-suspend wrapper for rule-script callers.
     *
     * `export.after` rules fire synchronously inside the rule engine's script
     * thread (a background worker via `Jsr223ScriptParser`), which is not a
     * suspend context. This entry point wraps [build] in `runBlocking` so rule
     * scripts can call `api.toCurl(...)` naturally.
     *
     * Safe because [PreScriptApplier.applyScripts] is EDT-free (no `swing { }`
     * hops) — `runBlocking` on a background thread never deadlocks with EDT.
     *
     * @param project Required only when running scripts; ignored otherwise.
     * @param endpoint The endpoint to build a cURL command for.
     * @param host Target host; defaults to [DEFAULT_HOST] when blank.
     * @param options Build options.
     * @return The formatted cURL command.
     */
    fun buildSync(
        project: Project?,
        endpoint: ApiEndpoint,
        host: String = DEFAULT_HOST,
        options: CurlBuildOptions = CurlBuildOptions(),
    ): String = kotlinx.coroutines.runBlocking {
        build(project, endpoint, host, options)
    }
}
