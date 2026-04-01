package com.itangcent.easyapi.settings

/**
 * HTTP client implementation types.
 *
 * Determines which HTTP client library is used for API calls.
 *
 * @param value The display name for the client type
 */
enum class HttpClientType(
    val value: String
) {
    /** Apache HttpClient - feature-rich, widely used */
    APACHE("Apache"),
    /** IntelliJ's built-in HTTP client (OkHttp-based) */
    DEFAULT("Default")
}
