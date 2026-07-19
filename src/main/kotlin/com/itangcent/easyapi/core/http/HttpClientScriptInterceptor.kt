package com.itangcent.easyapi.core.http

import com.itangcent.easyapi.core.rule.RuleKeys
import com.itangcent.easyapi.core.rule.engine.RuleEngine

/**
 * HTTP client wrapper that intercepts requests for script-based customization.
 *
 * Executes `http.call.before` and `http.call.after` rule events
 * around each HTTP request, allowing scripts to:
 * - Modify requests before sending
 * - Inspect responses after receiving
 * - Retry requests by discarding responses
 *
 * ## Rule Events
 * - `http.call.before` - Called before the request
 *   - Context: `request` (HttpRequestWrapper)
 * - `http.call.after` - Called after the response
 *   - Context: `request` (HttpRequestWrapper), `response` (HttpResponseWrapper)
 *
 * ## Retry Mechanism
 * If the response is discarded via `response.discard()`,
 * the request will be retried up to [MAX_RETRY] times.
 *
 * @param delegate The underlying HTTP client
 * @param ruleEngine The rule engine for script evaluation
 * @see RuleEngine for rule execution
 */
class HttpClientScriptInterceptor(
    private val delegate: HttpClient,
    private val ruleEngine: RuleEngine?
) : HttpClient {
    override suspend fun execute(request: HttpRequest): HttpResponse {
        // Recursion guard (Spec: ai-workflow-patterns, D3 / review Issue #3).
        // `depth` is a per-thread counter incremented at the top of execute() and
        // decremented in `finally`. It prevents infinite re-entry when a script
        // running in `http.call.before`/`after` issues a sub-request via
        // ScriptHttpClient.executeSync(...) (which re-enters this same interceptor).
        //   d == 0  → top-level request           (run hooks)
        //   d == 1  → first script-spawned sub-request (run hooks)
        //   d >= 2  → nested sub-request           (SKIP hooks — pass-through)
        val d = depth.get()
        depth.set(d + 1)
        try {
            val wrappedRequest = HttpRequestWrapper(request)
            var retryCount = 0
            while (true) {
                if (d < MAX_HOOK_DEPTH) {
                    ruleEngine?.evaluate(RuleKeys.HTTP_CALL_BEFORE) { ctx ->
                        ctx.setExt("request", wrappedRequest)
                    }
                }
                // Send the (possibly mutated) wrapper state, not the original request,
                // so retries carry script-applied header changes (Spec: D2).
                val response = delegate.execute(wrappedRequest.toHttpRequest())
                val wrappedResponse = HttpResponseWrapper(response, wrappedRequest)
                if (d < MAX_HOOK_DEPTH) {
                    ruleEngine?.evaluate(RuleKeys.HTTP_CALL_AFTER) { ctx ->
                        ctx.setExt("request", wrappedRequest)
                        ctx.setExt("response", wrappedResponse)
                    }
                }
                if (wrappedResponse.isDiscarded() && retryCount < MAX_RETRY) {
                    retryCount++
                    continue
                }
                return response
            }
        } finally {
            depth.set(d)
        }
    }

    override fun close() {
        delegate.close()
    }

    companion object {
        private const val MAX_RETRY = 3

        /** Per-thread recursion depth. Incremented/decremented in [execute]. */
        private val depth = ThreadLocal.withInitial { 0 }

        /**
         * Hooks run only when `d < MAX_HOOK_DEPTH` (d = depth read at top of execute()).
         * d=0 (top-level) and d=1 (first sub-request) run hooks; d>=2 skips them.
         */
        private const val MAX_HOOK_DEPTH = 2
    }
}

/**
 * Wrapper for HttpRequest providing script-friendly accessors + mutable headers.
 *
 * Used in rule scripts to inspect and modify request properties. Header mutation
 * (via [setHeader] / [removeHeader]) is reflected in [headers] and materialized
 * into a fresh [HttpRequest] by [toHttpRequest], so retries carry script-applied
 * header changes (Spec: ai-workflow-patterns, D2 — 401-refresh sets a new
 * `Authorization` header on the wrapper, then `response.discard()` triggers a
 * retry that sends the mutated headers).
 *
 * @param delegate The underlying HTTP request
 */
class HttpRequestWrapper(private val delegate: HttpRequest) {
    // Mutable header list — initialized from the delegate's headers. All other
    // fields are read-only passthroughs (only headers are script-mutable).
    private var headerOverrides: List<KeyValue> = delegate.headers

    fun url(): String = delegate.url
    fun method(): String = delegate.method
    fun headers(): List<KeyValue> = headerOverrides
    fun query(): List<KeyValue> = delegate.query
    fun body(): String? = delegate.body
    fun formParams(): List<FormParam> = delegate.formParams
    fun cookies(): List<HttpCookie> = delegate.cookies
    fun contentType(): String? = delegate.contentType

    /**
     * Upserts a header (case-insensitive on [name]). If one or more headers with
     * [name] already exist (ignoring case), they are ALL replaced by a single new
     * `name → value` entry (deduplicating case variants); otherwise a new
     * `name → value` header is appended.
     */
    fun setHeader(name: String, value: String) {
        val hasMatch = headerOverrides.any { it.name.equals(name, ignoreCase = true) }
        headerOverrides = if (hasMatch) {
            // Remove ALL case-variant matches, then append a single canonical entry.
            headerOverrides.filterNot { it.name.equals(name, ignoreCase = true) } +
                KeyValue(name, value)
        } else {
            headerOverrides + KeyValue(name, value)
        }
    }

    /**
     * Removes all headers matching [name] (case-insensitive). No-op if none match.
     */
    fun removeHeader(name: String) {
        headerOverrides = headerOverrides.filterNot { it.name.equals(name, ignoreCase = true) }
    }

    /**
     * Builds a fresh immutable [HttpRequest] copying all fields, using the current
     * [headerOverrides] for headers. Called by [HttpClientScriptInterceptor.execute]
     * on every attempt (including retries) so the delegate receives the latest
     * script-mutated header state.
     */
    fun toHttpRequest(): HttpRequest = HttpRequest(
        url = delegate.url,
        method = delegate.method,
        headers = headerOverrides,
        query = delegate.query,
        body = delegate.body,
        formParams = delegate.formParams,
        cookies = delegate.cookies,
        contentType = delegate.contentType
    )
}

/**
 * Wrapper for HttpResponse providing script-friendly accessors.
 *
 * Used in rule scripts to inspect responses and control retry behavior.
 *
 * ## Retry Control
 * Call `discard()` to mark the response for retry.
 * The request will be re-executed up to [MAX_RETRY] times.
 *
 * @param delegate The underlying HTTP response
 * @param request The associated request wrapper
 */
class HttpResponseWrapper(
    private val delegate: HttpResponse,
    private val request: HttpRequestWrapper
) {
    private var discarded = false

    fun code(): Int = delegate.code
    fun headers(): Map<String, List<String>> = delegate.headers
    fun body(): String? = delegate.body
    fun request(): HttpRequestWrapper = request

    fun discard() {
        this.discarded = true
    }

    fun isDiscarded(): Boolean = this.discarded
}
