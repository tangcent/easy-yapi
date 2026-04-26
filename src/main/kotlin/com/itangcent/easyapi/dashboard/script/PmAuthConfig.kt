package com.itangcent.easyapi.dashboard.script

/**
 * Authentication configuration for a request, compatible with Postman's `pm.request.auth` API.
 *
 * Supports three authentication types:
 * - **bearer**: Token-based authentication via `Authorization: Bearer <token>`
 * - **basic**: Username/password authentication via `Authorization: Basic <credentials>`
 * - **apikey**: API key authentication, either as a header or query parameter
 *
 * Groovy usage:
 * ```groovy
 * pm.request.auth.bearer("my-jwt-token")
 * pm.request.auth.basic("admin", "password123")
 * pm.request.auth.apiKey("X-API-Key", "abc123", "header")
 * ```
 *
 * After configuration, call [applyToHeaders] to inject the authentication into the request headers.
 */
class PmAuthConfig {

    private var authType: String = "noauth"
    private var authData: MutableMap<String, String> = mutableMapOf()

    /**
     * Configures API key authentication.
     *
     * @param key The header or parameter name (e.g., "X-API-Key")
     * @param value The API key value
     * @param location Where to place the key: "header" (default) or "query"
     */
    fun apiKey(key: String, value: String, location: String = "header") {
        authType = "apikey"
        authData["key"] = key
        authData["value"] = value
        authData["in"] = location
    }

    /**
     * Configures Bearer token authentication.
     *
     * @param token The bearer token value
     */
    fun bearer(token: String) {
        authType = "bearer"
        authData["token"] = token
    }

    /**
     * Configures Basic authentication.
     *
     * @param username The username
     * @param password The password
     */
    fun basic(username: String, password: String) {
        authType = "basic"
        authData["username"] = username
        authData["password"] = password
    }

    /** Returns the current authentication type: "noauth", "bearer", "basic", or "apikey". */
    fun type(): String = authType

    /** Returns an immutable snapshot of the authentication data. */
    fun data(): Map<String, String> = authData.toMap()

    /**
     * Applies the configured authentication to the given header list.
     *
     * - **bearer**: Adds `Authorization: Bearer <token>`
     * - **basic**: Adds `Authorization: Basic <base64(username:password)>`
     * - **apikey** with location "header": Adds the key-value pair as a header
     *
     * @param headers The request headers to modify
     */
    fun applyToHeaders(headers: PmHeaderList) {
        when (authType) {
            "apikey" -> {
                val location = authData["in"] ?: "header"
                if (location == "header") {
                    headers.upsert(authData["key"] ?: return, authData["value"] ?: return)
                }
            }
            "bearer" -> {
                headers.upsert("Authorization", "Bearer ${authData["token"] ?: return}")
            }
            "basic" -> {
                val credentials = java.util.Base64.getEncoder()
                    .encodeToString("${authData["username"] ?: ""}:${authData["password"] ?: ""}".toByteArray())
                headers.upsert("Authorization", "Basic $credentials")
            }
        }
    }
}
