package com.itangcent.easyapi.core.http

import kotlinx.coroutines.runBlocking

/**
 * Synchronous adapter exposing the suspend-only [HttpClient] API at the blocking
 * JSR-223 script boundary.
 *
 * ## Why this exists
 * The 401-refresh recipe runs inside an `http.call.after` rule value. That script is
 * evaluated by [com.itangcent.easyapi.core.rule.parser.Jsr223ScriptParser] via the JSR-223
 * Groovy engine, which is a **blocking** evaluator (`ScriptEngine.eval`). Groovy scripts
 * cannot call `suspend` functions directly. To let a 401-refresh script issue a
 * synchronous sub-request to the refresh-token endpoint, the suspend `HttpClient.execute`
 * must be bridged to a plain blocking call.
 *
 * `ScriptHttpClient` is that bridge: it hands out [HttpRequestBuilder]s
 * ([newRequest]) whose terminal [HttpRequestBuilder.execute] wraps the delegate's suspend
 * `execute` in `runBlocking { ... }`, so a Groovy script can write:
 *
 * ```groovy
 * def resp = httpClient.newRequest("https://api.example.com/refresh")
 *     .post()
 *     .form("grant_type", "refresh_token")
 *     .execute()
 * def newToken = new groovy.json.JsonSlurper().parseText(resp?.body).access_token
 * ```
 *
 * ## Threading
 * `runBlocking` blocks the calling thread until the coroutine completes. Rule scripts
 * already run on [com.itangcent.easyapi.core.internal.threading.IdeDispatchers.Background] (see
 * [com.itangcent.easyapi.core.rule.parser.Jsr223ScriptParser.parse]), so this does not block
 * the EDT. A recursion guard in [HttpClientScriptInterceptor] prevents infinite re-entry
 * when the sub-request itself triggers `http.call.before`/`after` hooks (depth-limited).
 *
 * ## Scope
 * Bound as `httpClient` ONLY in [com.itangcent.easyapi.core.rule.parser.Jsr223ScriptParser]
 * (for `groovy:` rule values + `http.call.before`/`after` events). It is intentionally
 * NOT bound in [com.itangcent.easyapi.core.script.pm.PmScriptExecutor] — `postman.*` scripts
 * use `pm.sendRequest` for sub-requests if ever needed.
 *
 * @param delegate the underlying suspend [HttpClient] (typically obtained from
 *   [HttpClientProvider.getInstance].getClient())
 */
class ScriptHttpClient(private val delegate: HttpClient) {

    /**
     * Executes [request] synchronously by bridging the delegate's suspend `execute`
     * through `runBlocking`. Blocks the calling thread until the response is available.
     *
     * @param request the [HttpRequest] to send
     * @return the [HttpResponse] returned by the delegate
     */
    fun executeSync(request: HttpRequest): HttpResponse = runBlocking { delegate.execute(request) }

    /**
     * Starts an OkHttp-style fluent request bound to this client:
     *
     * ```groovy
     * def resp = httpClient.newRequest("https://api.example.com/refresh")
     *     .post()
     *     .form("grant_type", "refresh_token")
     *     .execute()
     * ```
     *
     * Groovy-friendly on purpose: no default parameters, no lambdas, plain chaining, and
     * the terminal [HttpRequestBuilder.execute] bridges back through [runBlocking] — so the
     * script never has to build an [HttpRequest] by hand (its 8-arg positional constructor
     * is order-sensitive and has no no-arg overload for Groovy named arguments).
     *
     * @param url the target URL
     * @return a builder whose [HttpRequestBuilder.execute] runs synchronously on this client
     */
    fun newRequest(url: String): HttpRequestBuilder = delegate.newRequest(url)

    /**
     * Same as [newRequest] without an initial URL — set it later via
     * [HttpRequestBuilder.url].
     */
    fun newRequest(): HttpRequestBuilder = delegate.newRequest()
}
