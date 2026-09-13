package com.itangcent.easyapi.core.http

import kotlinx.coroutines.runBlocking

/**
 * Builder for [HttpRequest] — usable both **fluently** (Groovy rule scripts) and with a
 * **configuration block** (Kotlin call sites).
 *
 * ## Why this exists
 * [HttpRequest] is a Kotlin `data class`, so it has **no no-arg constructor** and Groovy's
 * named-argument form (`new HttpRequest(url: …, method: 'POST')`) does not work. A rule
 * script therefore had to call the 8-arg positional constructor, which is unreadable AND
 * silently breaks if a field is ever reordered (`url`/`method` and `body`/`contentType` are
 * all `String`, so a mismatch still type-checks at runtime).
 *
 * ## Kotlin usage (configuration block)
 * ```kotlin
 * val response = httpClient.execute {
 *     url = "https://api.example.com/refresh"
 *     post()
 *     form("grant_type", "refresh_token")
 * }
 * ```
 *
 * ## Groovy usage (fluent chaining)
 * ```groovy
 * def resp = httpClient.newRequest("https://api.example.com/refresh")
 *     .post()
 *     .form("grant_type", "refresh_token")
 *     .execute()
 * ```
 *
 * Groovy cannot use the block form: a closure resolves `url = "x"` against its *owner*, not
 * against the builder, and cannot call `suspend` functions. Both forms produce the same
 * [HttpRequest].
 *
 * ## Groovy compatibility contract
 * Called from the blocking JSR-223 Groovy engine
 * ([com.itangcent.easyapi.core.rule.parser.Jsr223ScriptParser]), so **every public method
 * must stay plain**: no default parameter values, no `suspend`, no lambdas, no `reified`,
 * and every mutator returns `this`. (Guarded by `HttpRequestBuilderTest`.)
 *
 * @param executor optional [HttpExecutor] used by [execute]. When null, only [build] is
 *   available — which is all the Kotlin `execute { }` extension needs, since it sends the
 *   built request through the executor itself.
 */
class HttpRequestBuilder(private val executor: HttpExecutor? = null) {

    // ------------------------------------------------- mutable configuration

    /** Target URL. */
    var url: String = ""

    /** HTTP method. Prefer the verb shortcuts (`get()`, `post()`, …). */
    var method: String = "GET"

    /** Raw request body — does NOT imply a content type (use [json] or [contentType]). */
    var body: String? = null

    /**
     * The `Content-Type` header value, delegated to the internal [headers] list as the single
     * source of truth.
     *
     * - A non-null assignment **upserts** the `Content-Type` header (deduping case variants).
     * - A `null` assignment **removes** it — `null` means "clear the header", nothing else.
     *   Callers that merely hold a *hint* (endpoint metadata, the encoding a form expects)
     *   and must not drop a user- or script-supplied header have to guard the assignment
     *   themselves, because the header list is the source of truth:
     *
     *   ```kotlin
     *   if (contentType == null) hint?.takeIf { it.isNotBlank() }?.let { contentType = it }
     *   ```
     * - Leaving it `null` (never assigned) also lets the client auto-detect the encoding
     *   from the form params — see [isMultipart].
     */
    var contentType: String?
        get() = headers.firstOrNull { it.name.equals("Content-Type", ignoreCase = true) }?.value
        set(value) {
            headers.removeAll { it.name.equals("Content-Type", ignoreCase = true) }
            if (value != null) headers.add(kv("Content-Type", value))
        }

    // ------------------------------------------------------------- internals

    private val headers = ArrayList<KeyValue>()
    private val query = ArrayList<KeyValue>()
    private val formParams = ArrayList<FormParam>()
    private val cookies = ArrayList<HttpCookie>()

    // ---------------------------------------------------------------- target

    /** Sets the request URL (fluent form of the [url] property). */
    fun url(url: String): HttpRequestBuilder = apply { this.url = url }

    /** Sets the HTTP method (fluent form of the [method] property). */
    fun method(method: String): HttpRequestBuilder = apply { this.method = method }

    fun get(): HttpRequestBuilder = method("GET")
    fun post(): HttpRequestBuilder = method("POST")
    fun put(): HttpRequestBuilder = method("PUT")
    fun delete(): HttpRequestBuilder = method("DELETE")
    fun patch(): HttpRequestBuilder = method("PATCH")
    fun head(): HttpRequestBuilder = method("HEAD")
    fun options(): HttpRequestBuilder = method("OPTIONS")

    // --------------------------------------------------------------- headers

    /** Appends a header (duplicates allowed — use [setHeader] to upsert). */
    fun header(name: String, value: String): HttpRequestBuilder = apply { headers.add(kv(name, value)) }

    /** Appends every entry of [values] as a header. */
    fun headers(values: Map<String, String>): HttpRequestBuilder = apply {
        values.forEach { (name, value) -> headers.add(kv(name, value)) }
    }

    /** Appends every entry of [values] as a header (bulk form for pre-computed lists). */
    fun headers(values: List<KeyValue>): HttpRequestBuilder = apply { headers.addAll(values) }

    /** Upserts a header (case-insensitive on [name]), replacing existing case variants. */
    fun setHeader(name: String, value: String): HttpRequestBuilder = apply {
        headers.removeAll { it.name.equals(name, ignoreCase = true) }
        headers.add(kv(name, value))
    }

    /** Removes all headers matching [name] (case-insensitive). */
    fun removeHeader(name: String): HttpRequestBuilder = apply {
        headers.removeAll { it.name.equals(name, ignoreCase = true) }
    }

    // ----------------------------------------------------------------- query

    /** Appends a query parameter. */
    fun query(name: String, value: String): HttpRequestBuilder = apply { query.add(kv(name, value)) }

    /** Appends every entry of [values] as a query parameter. */
    fun query(values: Map<String, String>): HttpRequestBuilder = apply {
        values.forEach { (name, value) -> query.add(kv(name, value)) }
    }

    /** Appends every entry of [values] as a query parameter (bulk form for pre-computed lists). */
    fun query(values: List<KeyValue>): HttpRequestBuilder = apply { query.addAll(values) }

    // ------------------------------------------------------------------ body

    /** Sets the raw request body (fluent form of the [body] property). */
    fun body(body: String?): HttpRequestBuilder = apply { this.body = body }

    /** Sets a JSON body and the matching `application/json` content type. */
    fun json(body: String?): HttpRequestBuilder = apply {
        this.body = body
        this.contentType = CONTENT_TYPE_JSON
    }

    /**
     * Sets the content type (fluent form of the [contentType] property).
     *
     * Replaces any existing `Content-Type` header; passing `null` clears it.
     */
    fun contentType(contentType: String?): HttpRequestBuilder = apply { this.contentType = contentType }

    // ------------------------------------------------------------ form params

    /** Adds a text form field (encoded as `x-www-form-urlencoded` unless a file part exists). */
    fun form(name: String, value: String): HttpRequestBuilder = apply {
        formParams.add(FormParam.Text(name, value))
    }

    /** Adds every entry of [values] as a text form field. */
    fun form(values: Map<String, String>): HttpRequestBuilder = apply {
        values.forEach { (name, value) -> formParams.add(FormParam.Text(name, value)) }
    }

    /** Appends pre-built form params (bulk form — keeps text/file parts as-is). */
    fun formParams(values: List<FormParam>): HttpRequestBuilder = apply { formParams.addAll(values) }

    /** Adds a file form field (switches the request to `multipart/form-data`). */
    fun file(name: String, fileName: String, bytes: ByteArray): HttpRequestBuilder =
        file(name, fileName, bytes, null)

    /** Adds a file form field with an explicit part content type. */
    fun file(name: String, fileName: String, bytes: ByteArray, contentType: String?): HttpRequestBuilder = apply {
        formParams.add(FormParam.File(name, fileName, contentType, bytes))
    }

    // --------------------------------------------------------------- cookies

    /** Adds a cookie. */
    fun cookie(name: String, value: String): HttpRequestBuilder = cookie(name, value, null, null)

    /** Adds a cookie with domain and path. */
    fun cookie(name: String, value: String, domain: String?, path: String?): HttpRequestBuilder = apply {
        cookies.add(HttpCookie(name, value, domain, path))
    }

    // -------------------------------------------------------------- terminal

    /** Materializes the immutable [HttpRequest]. */
    fun build(): HttpRequest = HttpRequest(
        url = url,
        method = method,
        headers = headers.toList(),
        query = query.toList(),
        body = body,
        formParams = formParams.toList(),
        cookies = cookies.toList()
    )

    /**
     * Builds and sends the request, blocking until the response is available.
     *
     * Bridges the [HttpExecutor.execute] suspend call via `runBlocking` — safe here because
     * rule scripts already run on
     * [com.itangcent.easyapi.core.internal.threading.IdeDispatchers.Background].
     *
     * @throws IllegalStateException when no [HttpExecutor] is bound — use [build] and send
     *   the request yourself, or create the builder via `executor.newRequest(url)`.
     */
    fun execute(): HttpResponse {
        val client = executor
            ?: throw IllegalStateException(
                "HttpRequestBuilder has no bound HttpExecutor — create it via " +
                    "httpClient.newRequest(url) or call build() and pass the request to httpClient.execute(request)"
            )
        return runBlocking { client.execute(build()) }
    }

    companion object {
        const val CONTENT_TYPE_JSON = "application/json; charset=utf-8"
        const val CONTENT_TYPE_FORM = "application/x-www-form-urlencoded; charset=utf-8"
    }
}
