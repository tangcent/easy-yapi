package com.itangcent.easyapi.dashboard.script

/**
 * Provides access to cookies associated with the current request/response cycle.
 *
 * Compatible with Postman's `pm.cookies` API. Cookies are typically populated
 * from the response `Set-Cookie` headers and can be queried by name.
 *
 * Groovy usage:
 * ```groovy
 * if (pm.cookies.has("session_id")) {
 *     def sid = pm.cookies.get("session_id")
 * }
 * def allCookies = pm.cookies.toObject()
 * ```
 *
 * @property cookies The backing cookie map (name → value)
 */
class PmCookies(
    private val cookies: Map<String, String> = emptyMap()
) {

    /** Checks whether a cookie with the given name exists. */
    fun has(name: String): Boolean = cookies.containsKey(name)

    /** Returns the value of the cookie with the given name, or null. */
    fun get(name: String): String? = cookies[name]

    /** Returns all cookies as an immutable map. */
    fun toObject(): Map<String, String> = cookies
}
