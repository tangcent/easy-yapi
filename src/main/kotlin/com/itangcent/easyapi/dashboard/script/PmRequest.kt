package com.itangcent.easyapi.dashboard.script

/**
 * Represents the HTTP request being prepared or sent, compatible with Postman's `pm.request` API.
 *
 * In pre-request scripts, all fields are mutable — the script can modify the URL, method,
 * headers, body, and authentication before the request is actually sent.
 *
 * Groovy usage:
 * ```groovy
 * pm.request.url = "https://api.example.com/v2/users"
 * pm.request.method = "POST"
 * pm.request.headers.upsert("Content-Type", "application/json")
 * pm.request.body.raw = '{"name": "Alice"}'
 * pm.request.auth.bearer("my-token")
 * ```
 *
 * @property url The full request URL
 * @property method The HTTP method (GET, POST, PUT, DELETE, etc.)
 * @property headers Mutable header list for the request
 * @property body Request body configuration (raw, urlencoded, or form-data)
 * @property auth Authentication configuration (bearer, basic, apikey)
 */
class PmRequest(
    var url: String = "",
    var method: String = "GET",
    val headers: PmHeaderList = PmHeaderList(),
    val body: PmRequestBody = PmRequestBody(),
    val auth: PmAuthConfig = PmAuthConfig()
)
