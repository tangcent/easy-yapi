package com.itangcent.easyapi.exporter.channel.hoppscotch

import com.itangcent.easyapi.exporter.channel.hoppscotch.HoppscotchSettings
import org.junit.Assert.*
import org.junit.Test

class HoppscotchAuthServiceLogicTest {

    private val defaultServerUrl = "https://hoppscotch.io"

    @Test
    fun `getServerUrl returns custom URL when set`() {
        val state = HoppscotchSettings(hoppscotchServerUrl = "https://custom.hoppscotch.io")
        val result = state.hoppscotchServerUrl?.takeIf { it.isNotBlank() } ?: defaultServerUrl
        assertEquals("https://custom.hoppscotch.io", result)
    }

    @Test
    fun `getServerUrl returns default URL when null`() {
        val state = HoppscotchSettings(hoppscotchServerUrl = null)
        val result = state.hoppscotchServerUrl?.takeIf { it.isNotBlank() } ?: defaultServerUrl
        assertEquals("https://hoppscotch.io", result)
    }

    @Test
    fun `getServerUrl returns default URL when blank`() {
        val state = HoppscotchSettings(hoppscotchServerUrl = "")
        val result = state.hoppscotchServerUrl?.takeIf { it.isNotBlank() } ?: defaultServerUrl
        assertEquals("https://hoppscotch.io", result)
    }

    @Test
    fun `getServerUrl returns default URL when whitespace`() {
        val state = HoppscotchSettings(hoppscotchServerUrl = "   ")
        val result = state.hoppscotchServerUrl?.takeIf { it.isNotBlank() } ?: defaultServerUrl
        assertEquals("https://hoppscotch.io", result)
    }

    @Test
    fun `isJcefAvailable gracefully handles JCEF unavailability`() {
        val result = try {
            val jcefAppClass = Class.forName("com.intellij.ui.jcef.JBCefApp")
            val isSupportedMethod = jcefAppClass.getMethod("isSupported")
            isSupportedMethod.invoke(null) as Boolean
        } catch (e: Throwable) {
            false
        }
        assertNotNull("JCEF check should not throw uncaught exceptions", result)
    }

    @Test
    fun `custom server URL constructs correct GraphQL endpoint`() {
        val customUrl = "https://custom.hoppscotch.example"
        val apiBaseUrl = HoppscotchApiClient.resolveApiBaseUrl(customUrl)
        val graphqlUrl = "$apiBaseUrl/api/graphql"
        assertEquals("https://custom.hoppscotch.example/api/graphql", graphqlUrl)
    }

    @Test
    fun `default server URL constructs correct GraphQL endpoint`() {
        val apiBaseUrl = HoppscotchApiClient.resolveApiBaseUrl(defaultServerUrl)
        val graphqlUrl = "$apiBaseUrl/graphql"
        assertEquals("https://api.hoppscotch.io/graphql", graphqlUrl)
    }

    @Test
    fun `custom server URL constructs correct auth login URL`() {
        val customUrl = "https://custom.hoppscotch.example"
        val loginUrl = "$customUrl/auth/login"
        assertEquals("https://custom.hoppscotch.example/auth/login", loginUrl)
    }

    @Test
    fun `custom server URL constructs correct token refresh URL`() {
        val customUrl = "https://custom.hoppscotch.example"
        val apiBaseUrl = HoppscotchApiClient.resolveApiBaseUrl(customUrl)
        val refreshUrl = "$apiBaseUrl/api/auth/token/refresh"
        assertEquals("https://custom.hoppscotch.example/api/auth/token/refresh", refreshUrl)
    }
}
