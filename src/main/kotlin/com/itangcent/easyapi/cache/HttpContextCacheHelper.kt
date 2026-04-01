package com.itangcent.easyapi.cache

import com.itangcent.easyapi.http.HttpCookie

/**
 * Helper for caching HTTP context data.
 *
 * Manages:
 * - Host names for quick selection
 * - HTTP cookies for session management
 *
 * ## Usage
 * ```kotlin
 * val cacheHelper = DefaultHttpContextCacheHelper.getInstance(project)
 *
 * // Add a host for future selection
 * cacheHelper.addHost("api.example.com")
 *
 * // Store cookies from a response
 * cacheHelper.addCookies(responseCookies)
 *
 * // Let user select a host
 * val selectedHost = cacheHelper.selectHost("Select API host")
 * ```
 *
 * @see DefaultHttpContextCacheHelper for the default implementation
 */
interface HttpContextCacheHelper {
    /**
     * Gets all cached host names.
     *
     * @return List of host names
     */
    fun getHosts(): List<String>

    /**
     * Adds a host name to the cache.
     *
     * @param host The host name to add
     */
    fun addHost(host: String)

    /**
     * Gets all cached cookies.
     *
     * @return List of HTTP cookies
     */
    fun getCookies(): List<HttpCookie>

    /**
     * Adds cookies to the cache.
     *
     * @param cookies The cookies to add
     */
    fun addCookies(cookies: List<HttpCookie>)

    /**
     * Shows a dialog for the user to select a host.
     *
     * @param message Optional message to display in the dialog
     * @return The selected host name
     */
    fun selectHost(message: String? = null): String
}

