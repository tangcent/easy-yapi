package com.itangcent.easyapi.core.http

/**
 * HTTP client interface for making HTTP requests.
 *
 * Multiple implementations are available:
 * - [ApacheHttpClient] - Uses Apache HttpClient
 * - [UrlConnectionHttpClient] - Uses Java URLConnection
 * - [IntelliJHttpClient] - Uses IntelliJ's built-in HTTP client
 *
 * The implementation is selected via settings (httpClient property).
 *
 * ## Usage
 * ```kotlin
 * val client = HttpClientProvider.getInstance(project).getClient()
 *
 * // builder (preferred)
 * val response = client.execute {
 *     url = "https://api.example.com/users"
 *     get()
 * }
 *
 * // or the two-step form
 * val response = client.execute(HttpRequest(url = "https://api.example.com/users"))
 * ```
 *
 * @see HttpClientProvider for client selection
 * @see HttpRequest for request model
 * @see HttpResponse for response model
 */
interface HttpClient : HttpExecutor, AutoCloseable {
    /**
     * Executes an HTTP request and returns the response.
     *
     * @param request The request to execute
     * @return The response
     */
    override suspend fun execute(request: HttpRequest): HttpResponse

    /**
     * Closes the client and releases resources.
     */
    override fun close()
}
