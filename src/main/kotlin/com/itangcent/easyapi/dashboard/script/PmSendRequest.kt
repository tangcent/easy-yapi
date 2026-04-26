package com.itangcent.easyapi.dashboard.script

import com.itangcent.easyapi.http.HttpClient
import com.itangcent.easyapi.http.HttpRequest
import com.itangcent.easyapi.http.KeyValue
import groovy.lang.Closure

/**
 * Provides the `pm.sendRequest` functionality for making additional HTTP requests from scripts.
 *
 * Compatible with Postman's `pm.sendRequest` API. Supports both simple URL-based calls
 * and option-map-based calls with method, headers, and body configuration.
 *
 * If no [HttpClient] is available, all calls are silently ignored (no-op).
 *
 * Groovy usage:
 * ```groovy
 * pm.sendRequest("https://api.example.com/token") { response ->
 *     pm.expect(response.code).to.equal(200)
 * }
 * pm.sendRequest([
 *     url: "https://api.example.com/data",
 *     method: "POST",
 *     header: [[key: "Content-Type", value: "application/json"]],
 *     body: [raw: '{"key": "value"}']
 * ]) { response ->
 *     pm.expect(response.code).to.equal(201)
 * }
 * ```
 *
 * @property httpClient The HTTP client used to execute requests; null results in no-op behavior
 */
class PmSendRequest(private val httpClient: HttpClient?) {

    /**
     * Sends a GET request to the given URL and invokes the callback with the response.
     *
     * @param url The target URL
     * @param closure Callback that receives a [PmResponse] (or an error response on failure)
     */
    operator fun invoke(url: String, closure: PmSendRequestCallback) {
        if (httpClient == null) return
        try {
            val request = HttpRequest(url = url, method = "GET")
            val response = kotlinx.coroutines.runBlocking {
                httpClient.execute(request)
            }
            val pmResponse = PmResponse(
                code = response.code,
                status = "",
                headers = PmHeaderList(response.headers.map { (k, v) -> k to v.joinToString(", ") }),
                responseTime = 0,
                responseSize = response.body?.length?.toLong() ?: 0,
                rawBody = response.body ?: ""
            )
            closure.call(pmResponse)
        } catch (e: Exception) {
            closure.call(
                PmResponse(
                    code = 0,
                    status = "Error",
                    headers = PmHeaderList(),
                    responseTime = 0,
                    responseSize = 0,
                    rawBody = e.message ?: "Unknown error"
                )
            )
        }
    }

    /**
     * Groovy-compatible overload accepting a [Closure].
     *
     * Groovy scripts pass `groovy.lang.Closure` objects, which cannot be auto-coerced
     * to [PmSendRequestCallback]. This overload wraps the closure automatically.
     */
    operator fun invoke(url: String, closure: Closure<*>) {
        invoke(url, closure.toCallback())
    }

    /**
     * Groovy-compatible alias for [invoke].
     *
     * Groovy's callable convention uses `call()` rather than Kotlin's `invoke()`,
     * so `pm.sendRequest("url") { }` in Groovy resolves to this method.
     */
    fun call(url: String, closure: PmSendRequestCallback) {
        invoke(url, closure)
    }

    /**
     * Groovy-compatible alias for [invoke] accepting a [Closure].
     */
    fun call(url: String, closure: Closure<*>) {
        invoke(url, closure)
    }

    /**
     * Sends a request configured via an options map and invokes the callback with the response.
     *
     * Supported options:
     * - `url` (required) — The target URL
     * - `method` — HTTP method (default: "GET")
     * - `header` — List of maps with "key" and "value" entries
     * - `body` — Map with a "raw" key for the request body
     *
     * @param options A map of request configuration options
     * @param closure Callback that receives a [PmResponse] (or an error response on failure)
     */
    operator fun invoke(options: Map<String, Any?>, closure: PmSendRequestCallback) {
        if (httpClient == null) return
        try {
            val url = options["url"]?.toString() ?: return
            val method = options["method"]?.toString() ?: "GET"
            val headerList = (options["header"] as? List<Map<String, String>>)?.map {
                KeyValue(it["key"] ?: "", it["value"] ?: "")
            } ?: emptyList()
            val body = (options["body"] as? Map<String, Any?>)?.get("raw")?.toString()
            val request = HttpRequest(
                url = url,
                method = method,
                headers = headerList,
                body = body
            )
            val response = kotlinx.coroutines.runBlocking {
                httpClient.execute(request)
            }
            val pmResponse = PmResponse(
                code = response.code,
                status = "",
                headers = PmHeaderList(response.headers.map { (k, v) -> k to v.joinToString(", ") }),
                responseTime = 0,
                responseSize = response.body?.length?.toLong() ?: 0,
                rawBody = response.body ?: ""
            )
            closure.call(pmResponse)
        } catch (e: Exception) {
            closure.call(
                PmResponse(
                    code = 0,
                    status = "Error",
                    headers = PmHeaderList(),
                    responseTime = 0,
                    responseSize = 0,
                    rawBody = e.message ?: "Unknown error"
                )
            )
        }
    }

    /**
     * Groovy-compatible overload accepting a [Closure].
     */
    operator fun invoke(options: Map<String, Any?>, closure: Closure<*>) {
        invoke(options, closure.toCallback())
    }

    /**
     * Groovy-compatible alias for [invoke].
     *
     * Groovy's callable convention uses `call()` rather than Kotlin's `invoke()`,
     * so `pm.sendRequest([url: "..."]) { }` in Groovy resolves to this method.
     */
    fun call(options: Map<String, Any?>, closure: PmSendRequestCallback) {
        invoke(options, closure)
    }

    /**
     * Groovy-compatible alias for [invoke] accepting a [Closure].
     */
    fun call(options: Map<String, Any?>, closure: Closure<*>) {
        invoke(options, closure)
    }

    private fun Closure<*>.toCallback(): PmSendRequestCallback {
        val callback = PmSendRequestCallback()
        callback.setHandler { response ->
            val params = if (maximumNumberOfParameters >= 1) arrayOf(response) else emptyArray()
            call(*params)
        }
        return callback
    }
}

/**
 * Callback handler for [PmSendRequest] responses.
 *
 * Groovy closures passed to `pm.sendRequest` are adapted into this callback type.
 * The [call] method invokes the registered handler with the received [PmResponse].
 */
class PmSendRequestCallback {

    private var handler: ((PmResponse) -> Unit)? = null

    /** Registers the handler function. */
    fun setHandler(h: (PmResponse) -> Unit) {
        handler = h
    }

    /** Invokes the registered handler with the given response. */
    fun call(response: PmResponse) {
        handler?.invoke(response)
    }
}
