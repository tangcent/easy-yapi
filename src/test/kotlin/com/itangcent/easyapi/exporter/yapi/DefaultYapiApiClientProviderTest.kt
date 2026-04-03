package com.itangcent.easyapi.exporter.yapi

import com.itangcent.easyapi.http.HttpClient
import com.itangcent.easyapi.http.HttpResponse
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.wrap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.mockito.kotlin.*

/**
 * Unit tests for [DefaultYapiApiClientProvider].
 *
 * Verifies token resolution, client caching per module, and failure propagation
 * when the server URL is not configured.
 *
 * Extends [EasyApiLightCodeInsightFixtureTestCase] (JUnit 3 style) — tests are
 * discovered by the `test` method name prefix, not by annotation.
 */
class DefaultYapiApiClientProviderTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var settingsHelper: YapiSettingsHelper
    private lateinit var httpClient: HttpClient

    override fun setUp() {
        super.setUp()
        httpClient = mock()
        settingsHelper = mock()
    }

    private fun buildProvider(serverUrl: String? = "http://yapi.example.com"): DefaultYapiApiClientProvider {
        runBlocking {
            whenever(settingsHelper.resolveServerUrl(any())).thenReturn(serverUrl)
            whenever(settingsHelper.resetPromptedModules()).then {}
        }
        val wrappedProject = wrap(project) {
            replaceService(YapiSettingsHelper::class, settingsHelper)
        }
        return DefaultYapiApiClientProvider(wrappedProject, actionContext)
    }

    fun testInitThrowsWhenServerUrlNotConfigured() {
        val provider = buildProvider(serverUrl = null)
        try {
            runBlocking { provider.init() }
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("server URL", ignoreCase = true))
        }
    }

    fun testInitSucceedsAndExposesServerUrl() = runBlocking {
        val provider = buildProvider("http://yapi.example.com")
        provider.init()
        assertEquals("http://yapi.example.com", provider.serverUrl)
    }

    fun testGetYapiApiClientReturnsNullWhenTokenCannotBeResolved() = runBlocking {
        val provider = buildProvider()
        provider.init()
        whenever(settingsHelper.resolveToken(any(), any())).thenReturn(null)

        val client = provider.getYapiApiClient("module-a")
        assertNull(client)
    }

    fun testGetYapiApiClientReturnsClientWhenTokenResolved() = runBlocking {
        val provider = buildProvider()
        provider.init()
        whenever(settingsHelper.resolveToken(eq("module-a"), any())).thenReturn("valid-token")

        val client = provider.getYapiApiClient("module-a")
        assertNotNull(client)
    }

    fun testGetYapiApiClientCachesClientPerModule() = runBlocking {
        val provider = buildProvider()
        provider.init()
        whenever(settingsHelper.resolveToken(eq("module-a"), any())).thenReturn("token-a")

        val first = provider.getYapiApiClient("module-a")
        val second = provider.getYapiApiClient("module-a")

        assertSame("Same instance should be returned for same module", first, second)
        verify(settingsHelper, times(1)).resolveToken(eq("module-a"), any())
        Unit
    }

    fun testGetYapiApiClientReturnsDifferentClientsForDifferentModules() = runBlocking {
        val provider = buildProvider()
        provider.init()
        whenever(settingsHelper.resolveToken(eq("module-a"), any())).thenReturn("token-a")
        whenever(settingsHelper.resolveToken(eq("module-b"), any())).thenReturn("token-b")

        val clientA = provider.getYapiApiClient("module-a")
        val clientB = provider.getYapiApiClient("module-b")

        assertNotNull(clientA)
        assertNotNull(clientB)
        assertNotSame(clientA, clientB)
    }

    fun testGetYapiApiClientUsesSelectedTokenBypassingSettingsLookup() = runBlocking {
        val provider = buildProvider()
        provider.init()

        val client = provider.getYapiApiClient("module-a", selectedToken = "pre-selected-token")

        assertNotNull(client)
        verify(settingsHelper, never()).resolveToken(any(), any())
        Unit
    }
}
