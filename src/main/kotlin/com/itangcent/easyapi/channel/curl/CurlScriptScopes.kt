package com.itangcent.easyapi.channel.curl

import com.itangcent.easyapi.core.internal.threading.readSync
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.script.ScriptScope

/**
 * Shared script-scope resolution for the cURL path.
 *
 * Centralizes the project→folder→class→endpoint scope formation so
 * [CurlChannel.export] (batch), [CurlExportResolver.formatForCopy] (single),
 * and [com.itangcent.easyapi.core.rule.context.ScriptApiEndpoint.toCurl]
 * (rule path) all derive scopes from one place. The dashboard's
 * `ApiDashboardPanel.resolveScriptScopesForEndpoint` is the prior art — this helper
 * replaces it (DRY).
 *
 * ## Threading
 *
 * [endpointKeyFor] reads `PsiClass.qualifiedName` via [readSync] (a synchronous read
 * action — acquires the read lock, does NOT dispatch to EDT). Safe to call from
 * background coroutines and rule-script threads. The EDT-free guarantee of
 * [PreScriptApplier.applyScripts] is preserved because scope resolution happens in
 * the caller, before [PreScriptApplier] is invoked.
 */
object CurlScriptScopes {

    /**
     * Resolves project + folder + class scopes for [endpoint] (outer → inner order,
     * matching [com.itangcent.easyapi.core.dashboard.RequestExecutor.resolveScripts]).
     *
     * The API Explorer persists a script set on the outermost project node as
     * `ScriptScope.Module(project.name)` — [ScriptScope] has no distinct project
     * scope and the root node carries no PSI context. Including that same key
     * here is what makes such a script apply to every endpoint below it. A null
     * or blank [projectName] (headless rule evaluation without a project) keeps
     * the previous folder + class behavior.
     *
     * The save side strips only the trailing " (<count>)" label suffix, so both
     * sides keep names containing " (" intact — no lossy truncation here.
     *
     * Used by the **batch export** path ([CurlChannel.export]) where per-endpoint
     * inline scripts are not consulted.
     */
    fun resolveFolderAndClassScopes(endpoint: ApiEndpoint, projectName: String?): List<ScriptScope> {
        val scopes = mutableListOf<ScriptScope>()
        projectName?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { scopes.add(ScriptScope.Module(it)) }
        // Folders use the same "<name> (<count>)" label on the save side, so trim
        // identically; the name itself is kept as-is (it may contain " (").
        endpoint.folder?.trim()?.takeIf { it.isNotEmpty() }?.let { scopes.add(ScriptScope.Module(it)) }
        val qualifiedName = endpoint.sourceClass?.let { readSync { it.qualifiedName } }
            ?: endpoint.className
        qualifiedName?.takeIf { it.isNotBlank() }?.let { scopes.add(ScriptScope.Class(it)) }
        // A folder named after the project would otherwise load (and run) the
        // same cached script twice.
        return scopes.distinctBy { it.key }
    }

    /**
     * Resolves project + folder + class + endpoint scopes for [endpoint]
     * (outer → inner order).
     *
     * Used by the **single-endpoint** path ([CurlExportResolver.formatForCopy],
     * where per-endpoint inline scripts ARE consulted
     * (mirrors `RequestExecutor.resolveScripts` which appends `input.preRequestScript`).
     *
     * The endpoint scope key is formed via [endpointKeyFor], matching the dashboard's
     * `EndpointDetailsPanel.computeCacheKey` so scripts saved by the dashboard UI
     * are found.
     */
    fun resolveAllScopes(endpoint: ApiEndpoint, projectName: String?): List<ScriptScope> {
        val scopes = resolveFolderAndClassScopes(endpoint, projectName).toMutableList()
        endpointKeyFor(endpoint).takeIf { it.isNotBlank() }?.let { scopes.add(ScriptScope.Endpoint(it)) }
        return scopes
    }

    /**
     * Forms the endpoint-scope key for [endpoint], matching the dashboard's
     * `EndpointDetailsPanel.computeCacheKey`: `"${qualifiedClassName}#${methodName}"`.
     *
     * Returns `""` when [ApiEndpoint.sourceMethod] or its containing class is unavailable
     * (e.g. synthetic endpoints) — the caller treats blank as "no endpoint scope".
     */
    fun endpointKeyFor(endpoint: ApiEndpoint): String {
        val method = endpoint.sourceMethod ?: return ""
        val cls = endpoint.sourceClass ?: method.containingClass ?: return ""
        val className = readSync { cls.qualifiedName ?: cls.name ?: "" }
        return "$className#${method.name}"
    }
}
