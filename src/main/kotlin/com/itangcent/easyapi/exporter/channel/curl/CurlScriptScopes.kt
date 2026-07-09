package com.itangcent.easyapi.exporter.channel.curl

import com.itangcent.easyapi.core.threading.readSync
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.script.ScriptScope

/**
 * Shared script-scope resolution for the cURL path.
 *
 * Centralizes the folder→class→endpoint scope formation so [CurlChannel.export]
 * (batch, folder+class only), [CurlExportResolver.formatForCopy] (single,
 * folder+class+endpoint), and [com.itangcent.easyapi.rule.context.ScriptApiEndpoint.toCurl]
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
     * Resolves folder + class scopes for [endpoint] (outer → inner order, matching
     * [com.itangcent.easyapi.dashboard.RequestExecutor.resolveScripts]).
     *
     * Used by the **batch export** path ([CurlChannel.export]) where per-endpoint
     * inline scripts are not consulted.
     */
    fun resolveFolderAndClassScopes(endpoint: ApiEndpoint): List<ScriptScope> {
        val scopes = mutableListOf<ScriptScope>()
        endpoint.folder?.takeIf { it.isNotBlank() }?.let { scopes.add(ScriptScope.Module(it)) }
        val qualifiedName = readSync { endpoint.sourceClass?.qualifiedName }
            ?: endpoint.className
        qualifiedName?.takeIf { it.isNotBlank() }?.let { scopes.add(ScriptScope.Class(it)) }
        return scopes
    }

    /**
     * Resolves folder + class + endpoint scopes for [endpoint] (outer → inner order).
     *
     * Used by the **single-endpoint** path ([CurlExportResolver.formatForCopy],
     * where per-endpoint inline scripts ARE consulted
     * (mirrors `RequestExecutor.resolveScripts` which appends `input.preRequestScript`).
     *
     * The endpoint scope key is formed via [endpointKeyFor], matching the dashboard's
     * `EndpointDetailsPanel.computeCacheKey` so scripts saved by the dashboard UI
     * are found.
     */
    fun resolveAllScopes(endpoint: ApiEndpoint): List<ScriptScope> {
        val scopes = resolveFolderAndClassScopes(endpoint).toMutableList()
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
