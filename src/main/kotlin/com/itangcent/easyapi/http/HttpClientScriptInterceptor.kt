package com.itangcent.easyapi.http

import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine

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
        val wrappedRequest = HttpRequestWrapper(request)
        var retryCount = 0
        while (true) {
            ruleEngine?.evaluate(RuleKeys.HTTP_CALL_BEFORE) { ctx ->
                ctx.setExt("request", wrappedRequest)
            }
            val response = delegate.execute(request)
            val wrappedResponse = HttpResponseWrapper(response, wrappedRequest)
            ruleEngine?.evaluate(RuleKeys.HTTP_CALL_AFTER) { ctx ->
                ctx.setExt("request", wrappedRequest)
                ctx.setExt("response", wrappedResponse)
            }
            if (wrappedResponse.isDiscarded() && retryCount < MAX_RETRY) {
                retryCount++
                continue
            }
            return response
        }
    }

    override fun close() {
        delegate.close()
    }

    companion object {
        private const val MAX_RETRY = 3
    }
}

/**
 * Wrapper for HttpRequest providing script-friendly accessors.
 *
 * Used in rule scripts to inspect and modify request properties.
 *
 * @param delegate The underlying HTTP request
 */
class HttpRequestWrapper(private val delegate: HttpRequest) {
    fun url(): String = delegate.url
    fun method(): String = delegate.method
    fun headers(): List<KeyValue> = delegate.headers
    fun query(): List<KeyValue> = delegate.query
    fun body(): String? = delegate.body
    fun formParams(): List<FormParam> = delegate.formParams
    fun cookies(): List<HttpCookie> = delegate.cookies
    fun contentType(): String? = delegate.contentType
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
