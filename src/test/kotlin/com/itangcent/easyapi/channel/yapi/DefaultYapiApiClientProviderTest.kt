package com.itangcent.easyapi.channel.yapi

import com.itangcent.easyapi.core.http.HttpClient
import com.itangcent.easyapi.core.http.HttpClientProvider
import com.itangcent.easyapi.core.http.HttpResponse
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

    private fun buildProvider(
        serverUrl: String? = "http://yapi.example.com",
        headless: Boolean = false,
        http: HttpClient? = null
    ): DefaultYapiApiClientProvider {
        runBlocking {
            whenever(settingsHelper.resolveServerUrl(any())).thenReturn(serverUrl)
            whenever(settingsHelper.resetPromptedModules()).then {}
        }
        val wrappedProject = wrap(project) {
            replaceService(YapiSettingsHelper::class, settingsHelper)
            if (http != null) {
                replaceService(HttpClientProvider::class, mock<HttpClientProvider> { on { getClient() } doReturn http })
            }
        }
        return DefaultYapiApiClientProvider(wrappedProject, headless)
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

    fun testInteractiveInitMayPromptForTheServerAndResetsPrompts() = runBlocking {
        val provider = buildProvider()

        provider.init()

        verify(settingsHelper).resolveServerUrl(false)
        verify(settingsHelper).resetPromptedModules()
        Unit
    }

    fun testInteractiveReasonIsTheNoValidTokenLine() = runBlocking {
        val provider = buildProvider()
        provider.init()
        whenever(settingsHelper.resolveToken(any(), any())).thenReturn(null)

        assertNull(provider.getYapiApiClient("module-a"))
        assertEquals("No valid token for module 'module-a'", provider.unavailableReason("module-a"))
    }

    // ---- headless: settings only, and a reason instead of a dialog ----

    fun testHeadlessInitReadsTheServerWithoutPromptingOrResettingPrompts() = runBlocking {
        val provider = buildProvider(headless = true)

        provider.init()

        verify(settingsHelper).resolveServerUrl(true)
        verify(settingsHelper, never()).resetPromptedModules()
        Unit
    }

    fun testHeadlessInitNamesTheSettingToFill() {
        val provider = buildProvider(serverUrl = null, headless = true)
        try {
            runBlocking { provider.init() }
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            val message = e.message!!
            assertTrue(message, message.contains("YAPI server URL"))
            assertTrue(message, message.contains("\"Yapi Server\" under Settings → EasyYapi → Yapi"))
        }
    }

    fun testHeadlessMissingTokenNamesTheDestinationAndBothRemedies() = runBlocking {
        val provider = buildProvider(headless = true)
        provider.init()
        whenever(settingsHelper.lookupToken("module-a")).thenReturn(null)

        assertNull(provider.getYapiApiClient("module-a"))

        val reason = provider.unavailableReason("module-a")
        assertTrue("names the destination: $reason", reason.contains("'module-a'"))
        assertTrue("offers the option: $reason", reason.contains("token option"))
        assertTrue("offers the settings line: $reason", reason.contains("\"module-a={token}\""))
        verify(settingsHelper, never()).resolveToken(any(), any())
        Unit
    }

    fun testHeadlessRejectedTokenSaysYapiRejectedItAndHowToReplaceIt() = runBlocking {
        whenever(httpClient.execute(any())).thenReturn(REJECTED)
        val provider = buildProvider(headless = true, http = httpClient)
        provider.init()
        whenever(settingsHelper.lookupToken("module-a")).thenReturn("token-a")

        assertNull(provider.getYapiApiClient("module-a"))

        val reason = provider.unavailableReason("module-a")
        assertTrue("says YApi rejected it: $reason", reason.contains("YApi rejected the token configured for 'module-a'"))
        assertTrue("carries the reason: $reason", reason.contains("token 无效"))
        assertTrue("names the line to update: $reason", reason.contains("module-a={token}"))
        assertFalse("not a server problem: $reason", reason.contains("Yapi Server"))
    }

    fun testHeadlessUnverifiedTokenPointsAtTheServer() = runBlocking {
        whenever(httpClient.execute(any())).thenThrow(RuntimeException("Connection refused"))
        val provider = buildProvider(headless = true, http = httpClient)
        provider.init()
        whenever(settingsHelper.lookupToken("module-a")).thenReturn("token-a")

        assertNull(provider.getYapiApiClient("module-a"))

        val reason = provider.unavailableReason("module-a")
        assertTrue("says the token was not checked: $reason", reason.contains("Could not check the token configured for 'module-a'"))
        assertTrue("carries the reason: $reason", reason.contains("Request to YApi server failed"))
        assertTrue("points at the server setting: $reason", reason.contains("\"Yapi Server\""))
        assertFalse("does not say YApi rejected it: $reason", reason.contains("rejected"))
    }

    fun testHeadlessFailureIsLookedUpOncePerDestination() = runBlocking {
        val provider = buildProvider(headless = true)
        provider.init()
        whenever(settingsHelper.lookupToken("module-a")).thenReturn(null)

        provider.getYapiApiClient("module-a")
        val again = provider.getYapiApiClient("module-a")

        assertNull("still no client", again)
        verify(settingsHelper, times(1)).lookupToken("module-a")
        Unit
    }

    fun testHeadlessAcceptedTokenGivesACachedClient() = runBlocking {
        whenever(httpClient.execute(any())).thenReturn(
            HttpResponse(code = 200, body = """{"errcode":0,"errmsg":"成功！","data":{"_id":"11"}}""")
        )
        val provider = buildProvider(headless = true, http = httpClient)
        provider.init()
        whenever(settingsHelper.lookupToken("module-a")).thenReturn("token-a")

        val client = provider.getYapiApiClient("module-a")

        assertNotNull(client)
        assertSame("cached per destination", client, provider.getYapiApiClient("module-a"))
        verify(settingsHelper, times(1)).lookupToken("module-a")
        Unit
    }

    fun testPassedTokenRejectedByYapiSaysSo() = runBlocking {
        whenever(httpClient.execute(any())).thenReturn(REJECTED)
        val provider = buildProvider(headless = true, http = httpClient)
        provider.init()

        val reason = provider.checkPassedToken("bad-token")

        assertNotNull("not accepted", reason)
        assertTrue("says YApi rejected it: $reason", reason!!.contains("YApi rejected the token passed for this export"))
        assertTrue("the server's own words: $reason", reason.contains("token 无效"))
        assertFalse("not a server problem: $reason", reason.contains("Yapi Server"))
    }

    private companion object {
        /** YApi's own answer to a token it refuses. */
        val REJECTED = HttpResponse(code = 200, body = """{"errcode":42014,"errmsg":"token 无效","data":null}""")
    }
}
