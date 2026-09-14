package com.itangcent.easyapi.core.http

/**
 * The ability to perform one HTTP exchange: `(HttpRequest) -> HttpResponse`.
 *
 * Extracted from [HttpClient] so that anything which can *send* a request — a real client,
 * a script-facing adapter, a test double — can be referenced without also carrying the
 * lifecycle ([AutoCloseable]) or implementation-selection concerns of [HttpClient].
 *
 * [HttpClient] extends this interface, so every client is an [HttpExecutor]; the reverse is
 * not required.
 */
interface HttpExecutor {

    /**
     * Executes [request] and returns the response.
     *
     * @param request the request to send
     * @return the response
     */
    suspend fun execute(request: HttpRequest): HttpResponse
}

/**
 * Builds a request with [block] and sends it through this executor.
 *
 * The one-shot replacement for the two-step form:
 *
 * ```kotlin
 * // before
 * val request = HttpRequest(url = "https://api.example.com/refresh", method = "POST",
 *     formParams = listOf(FormParam.Text("grant_type", "refresh_token")))
 * val response = httpClient.execute(request)
 *
 * // after
 * val response = httpClient.execute {
 *     url = "https://api.example.com/refresh"
 *     post()
 *     form("grant_type", "refresh_token")
 * }
 * ```
 *
 * @param block request configuration applied to a fresh [HttpRequestBuilder]
 * @return the response
 */
suspend fun HttpExecutor.execute(block: HttpRequestBuilder.() -> Unit): HttpResponse {
    val builder = HttpRequestBuilder(this)
    builder.block()
    return execute(builder.build())
}

/**
 * Sends a **GET** request configured by [block] — the verb-first form of [execute]:
 *
 * ```kotlin
 * val response = httpClient.get {
 *     url = "https://api.example.com/users"
 *     header("Accept", "application/json")
 * }
 * ```
 *
 * The method is preset to GET **before** [block] runs, so an explicit
 * `method = …` inside the block still wins.
 */
suspend fun HttpExecutor.get(block: HttpRequestBuilder.() -> Unit): HttpResponse =
    execute { get(); block() }

/**
 * Sends a **POST** request configured by [block] — the verb-first form of [execute]:
 *
 * ```kotlin
 * val response = httpClient.post {
 *     url = "https://api.example.com/refresh"
 *     json("""{"grant_type":"refresh_token"}""")
 * }
 * ```
 *
 * The method is preset to POST **before** [block] runs, so an explicit
 * `method = …` inside the block still wins.
 */
suspend fun HttpExecutor.post(block: HttpRequestBuilder.() -> Unit): HttpResponse =
    execute { post(); block() }

/**
 * Starts a fluent (OkHttp-style) request bound to this executor.
 *
 * This is the entry point for **Groovy rule scripts**, which cannot call `suspend`
 * functions nor use Kotlin lambda-with-receiver blocks (a Groovy closure resolves
 * `url = "x"` against its owner, not against the builder). The returned builder's
 * [HttpRequestBuilder.execute] bridges back through `runBlocking`.
 *
 * ```groovy
 * def resp = httpClient.newRequest("https://api.example.com/refresh")
 *     .post()
 *     .form("grant_type", "refresh_token")
 *     .execute()
 * ```
 *
 * @param url the target URL
 */
fun HttpExecutor.newRequest(url: String): HttpRequestBuilder = HttpRequestBuilder(this).url(url)

/**
 * Same as [newRequest] without an initial URL — set it later via
 * [HttpRequestBuilder.url].
 */
fun HttpExecutor.newRequest(): HttpRequestBuilder = HttpRequestBuilder(this)
