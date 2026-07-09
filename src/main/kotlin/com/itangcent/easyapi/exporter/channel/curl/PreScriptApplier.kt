package com.itangcent.easyapi.exporter.channel.curl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ApiHeader
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.MutableExtension
import com.itangcent.easyapi.logging.IdeaConsoleProvider
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.psi.model.ObjectModelJsonConverter
import com.itangcent.easyapi.script.ScriptCacheService
import com.itangcent.easyapi.script.ScriptScope
import com.itangcent.easyapi.script.env.EnvironmentService
import com.itangcent.easyapi.script.pm.LivePmVariableScope
import com.itangcent.easyapi.script.pm.PmInfo
import com.itangcent.easyapi.script.pm.PmObject
import com.itangcent.easyapi.script.pm.PmRequest
import com.itangcent.easyapi.script.pm.PmRequestBody
import com.itangcent.easyapi.script.pm.PmScriptExecutor
import com.itangcent.easyapi.script.pm.PmTestCollector
import com.itangcent.easyapi.script.pm.PmVariableScope

/**
 * Project-scoped service that applies pre-request scripts to an [ApiEndpoint]
 * producing a deep copy with script-driven url/method/headers/body
 * written back. The original endpoint is never mutated.
 *
 * Mirrors [com.itangcent.easyapi.dashboard.RequestExecutor.executeWithScripts]
 * (PmRequest build → script run → write-back), but writes to an endpoint deep copy
 * instead of an `HttpRequest`, and never sends the request (`httpClient = null`).
 *
 * ## Threading
 *
 * **EDT-free guarantee:** [applyScripts] SHALL NOT do `swing { }` / EDT hops — it reads
 * [ScriptCacheService] (sync) + runs Groovy via [PmScriptExecutor] (background-safe).
 * This guarantees [CurlBuilder.buildSync]'s `runBlocking` is deadlock-free when called
 * from rule-script threads (EDT-free constraint).
 *
 * ## Error handling
 *
 * If a script throws, [applyScripts] logs `console.warn` + `LOG.warn` and returns an
 * un-mutated deep copy. Per AGENTS.md this is a per-item recoverable
 * failure → no Notification. The cURL path uses `console.warn` (NOT `console.error`);
 * `RequestExecutor.executeWithScripts` uses `console.error` but that is pre-existing
 * and out of scope to change.
 *
 * ## Known limitations
 *
 * - **HTTP-only:** gRPC endpoints return a deep copy unchanged (no `PmRequest` analog).
 * - **Host rewrite dropped:** if a script changes the host portion of `pm.request.url`,
 *   that change is currently dropped on write-back (only the path is stored). Scripts
 *   that rewrite the host are rare; full host rewrite support would require carrying
 *   the host separately.
 */
@Service(Service.Level.PROJECT)
class PreScriptApplier(
    private val project: Project,
) : IdeaLog {

    /**
     * Resolves + runs the folder/API/endpoint pre-request scripts for [scopes] (in
     * outer→inner order, matching [com.itangcent.easyapi.dashboard.RequestExecutor.resolveScripts]),
     * then writes the script's resulting url/method/headers/body onto a DEEP COPY of
     * [endpoint]. The original [endpoint] is never mutated.
     *
     * Best-effort: if a script throws, logs `console.warn` with the scope and returns
     * the un-mutated deep copy for this endpoint.
     *
     * @param host the host to seed the PmRequest URL with (already resolved by caller).
     * @param scopes script scopes to resolve (folder → class → endpoint, outer→inner).
     * @return a deep copy of [endpoint] with script effects applied; or an un-mutated
     *         deep copy if no script ran, the endpoint is gRPC, or the script failed.
     */
    suspend fun applyScripts(endpoint: ApiEndpoint, host: String, scopes: List<ScriptScope>): ApiEndpoint {
        // Short-circuit: no scopes → nothing to run. Still return a deep copy so callers
        // can rely on the "original is never touched, never returned" contract.
        if (scopes.isEmpty()) return endpoint.deepCopy()

        val scriptCache = ScriptCacheService.getInstance(project)
        val resolved = scriptCache.resolveScripts(scopes)
        val preScript = resolved.preRequestScript
        if (preScript.isNullOrBlank()) return endpoint.deepCopy()  // nothing to run

        // HTTP-only: gRPC endpoints have no PmRequest analog.
        val http = endpoint.metadata as? HttpMetadata
            ?: return endpoint.deepCopy()

        // 1. Build PmRequest from endpoint (mirrors RequestExecutor ~line 184)
        val pmRequest = buildPmRequest(http, endpoint, host)

        // 2. Run pre-request script (best-effort, mirrors executeWithScripts ~line 249)
        val pm = buildPmObject(pmRequest, endpoint)
        try {
            PmScriptExecutor.getInstance(project).executePreRequestScript(preScript, pm)
        } catch (e: Exception) {
            val scopeDesc = scopes.joinToString(" → ") { it.displayLabel() }
            LOG.warn("curl: pre-script error in [$scopeDesc] for '${endpoint.name}': ${e.message}", e)
            IdeaConsoleProvider.getInstance(project).getConsole()
                .warn("curl: pre-script failed for '${endpoint.name}': ${e.message}")
            return endpoint.deepCopy()  // script failed → return un-mutated copy
        }

        // 3. Write script effects onto a deep copy
        return writeScriptEffects(endpoint, pmRequest, http, host)
    }

    /**
     * Builds a [PmRequest] from the endpoint's [HttpMetadata].
     *
     * - URL = `host + path` (so `pm.request.url` is meaningful to scripts; on write-back
     *   the host is stripped — see [writeScriptEffects]).
     * - Body = JSON-serialized `http.body` via [ObjectModelJsonConverter.toJson] (same
     *   source as [EndpointVariableResolver] uses for the resolved-body extension).
     * - Headers copied 1:1 from `http.headers`.
     */
    private fun buildPmRequest(http: HttpMetadata, endpoint: ApiEndpoint, host: String): PmRequest {
        val url = if (host.isBlank()) http.path else "$host${http.path}"
        val body = http.body?.let { ObjectModelJsonConverter.toJson(it) }
        val req = PmRequest(url = url, method = http.method.name, body = PmRequestBody(raw = body))
        http.headers.forEach { req.headers.add(it.name, it.value ?: "") }
        return req
    }

    /**
     * Builds a [PmObject.forPreRequest] context for the script run.
     *
     * - `httpClient = null` — cURL conversion never sends a request.
     * - `environment = LivePmVariableScope(EnvironmentService)` so scripts can read/write
     *   env vars the same way they do in [RequestExecutor].
     * - `globals`/`collectionVariables` are empty scopes (no collection concept in cURL path).
     */
    private fun buildPmObject(pmRequest: PmRequest, endpoint: ApiEndpoint): PmObject {
        val envVars = LivePmVariableScope(EnvironmentService.getInstance(project))
        val info = PmInfo(eventName = "prerequest", requestName = endpoint.name ?: "", requestId = "")
        return PmObject.forPreRequest(
            request = pmRequest,
            environment = envVars,
            globals = PmVariableScope(),
            collectionVariables = PmVariableScope(),
            testCollector = PmTestCollector(),
            info = info,
            httpClient = null,
        )
    }

    /**
     * Writes the script's resulting url/method/headers/body onto a DEEP COPY of [endpoint].
     * The original [endpoint] is never touched.
     *
     * - **path:** `pmRequest.url` with the host prefix stripped (the formatter re-prepends host).
     *   If the URL is blank, fall back to the original path.
     * - **method:** mapped back to [HttpMethod] (case-insensitive); blank falls back to original.
     * - **headers:** replaced wholesale if the script produced any (matches `RequestExecutor`
     *   line 268: `modifiedHeaders.ifEmpty { originalHeaders }` — but here we keep original
     *   on empty so the deep copy stays self-consistent).
     * - **body:** stashed via [EndpointVariableResolver.RESOLVED_BODY_JSON_KEY] extension
     *   (reuses the existing carrier; [CurlFormatter.buildBody] already checks this key first).
     */
    private fun writeScriptEffects(
        endpoint: ApiEndpoint,
        pmRequest: PmRequest,
        originalHttp: HttpMetadata,
        host: String,
    ): ApiEndpoint {
        val copy = endpoint.deepCopy()
        val copyHttp = copy.metadata as? HttpMetadata ?: return copy

        // path (strip host prefix; blank → fall back to original)
        val modifiedUrl = pmRequest.url.ifBlank { host + originalHttp.path }
        copyHttp.path = stripHost(modifiedUrl, host, originalHttp.path)

        // method
        pmRequest.method.takeIf { it.isNotBlank() }?.let { m ->
            HttpMethod.values().find { it.name.equals(m, ignoreCase = true) }?.let { copyHttp.method = it }
        }

        // headers (replace only if script produced any)
        val scriptedHeaders = pmRequest.headers.toPairs()
        if (scriptedHeaders.isNotEmpty()) {
            copyHttp.headers.clear()
            scriptedHeaders.forEach { (k, v) -> copyHttp.headers.add(ApiHeader(k, v)) }
        }

        // body via RESOLVED_BODY_JSON_KEY extension (reuses the existing carrier).
        // deepCopy() installed a fresh MutableExtension, so the cast is always safe.
        pmRequest.body.raw?.takeIf { it.isNotBlank() }?.let { bodyRaw ->
            (copy.extensions as MutableExtension)[EndpointVariableResolver.RESOLVED_BODY_JSON_KEY] = bodyRaw
        }
        return copy
    }

    /**
     * Strips the [host] prefix from [url], returning the path-only portion.
     *
     * - If [url] starts with [host], returns the suffix (with leading `/` preserved).
     * - If [url] does not start with [host] (script rewrote host), returns [fallback]
     *   — host rewrite is a known dropped effect (KDoc above).
     * - If [host] is blank, returns [url] as-is (no host to strip).
     */
    private fun stripHost(url: String, host: String, fallback: String): String {
        if (host.isBlank()) return url
        if (!url.startsWith(host)) return fallback  // host rewritten → drop (known limitation)
        return url.substring(host.length).ifBlank { fallback }
    }

    /**
     * Deep-copies an [ApiEndpoint] so script mutations never touch the original.
     *
     * - [HttpMetadata] is copied with new mutable `headers`/`parameters` lists (each
     *   element copied so header/parameter instances are not shared).
     * - [ApiEndpoint.extensions] is replaced with a fresh [MutableExtension] carrying
     *   copies of the original's entries, so writes via
     *   `(extensions as MutableExtension)[key] = value` land on the copy, not the original.
     */
    private fun ApiEndpoint.deepCopy(): ApiEndpoint {
        val copiedMeta = (this.metadata as? HttpMetadata)?.let { http ->
            http.copy(
                headers = http.headers.map { it.copy() }.toMutableList(),
                parameters = http.parameters.map { it.copy() }.toMutableList(),
            )
        } ?: this.metadata
        val copiedExt: MutableExtension = MutableExtension().also { it.putAll(this.extensions.exts) }
        return this.copy(metadata = copiedMeta, extensions = copiedExt)
    }

    companion object {
        fun getInstance(project: Project): PreScriptApplier = project.service()
    }
}
