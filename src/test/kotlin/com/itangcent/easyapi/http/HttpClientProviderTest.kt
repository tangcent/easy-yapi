package com.itangcent.easyapi.http

import com.itangcent.easyapi.settings.HttpClientType
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class HttpClientProviderTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var httpClientProvider: HttpClientProvider

    override fun setUp() {
        super.setUp()
        httpClientProvider = HttpClientProvider.getInstance(project)
    }

    override fun tearDown() {
        httpClientProvider.dispose()
        super.tearDown()
    }

    fun testGetClientReturnsNonNull() {
        val client = httpClientProvider.getClient()
        assertNotNull("getClient should return a non-null HttpClient", client)
        client.close()
    }

    fun testGetClientReturnsApacheByDefault() {
        val client = httpClientProvider.getClient()
        assertNotNull("Default client should be non-null", client)
        client.close()
    }

    fun testGetClientWithApacheType() {
        val client = httpClientProvider.getClient(httpClient = HttpClientType.APACHE.value)
        assertNotNull("Apache client should be non-null", client)
        client.close()
    }

    fun testGetClientWithCustomTimeout() {
        val client = httpClientProvider.getClient(httpTimeOut = 10)
        assertNotNull("Client with custom timeout should be non-null", client)
        client.close()
    }

    fun testGetClientWithUnsafeSsl() {
        val client = httpClientProvider.getClient(unsafeSsl = true)
        assertNotNull("Client with unsafe SSL should be non-null", client)
        client.close()
    }

    fun testDisposeCleansUp() {
        val client = httpClientProvider.getClient()
        assertNotNull("Client should be created before dispose", client)
        httpClientProvider.dispose()
    }

    fun testGetClientAfterDispose() {
        val client1 = httpClientProvider.getClient()
        assertNotNull("Client before dispose should be non-null", client1)
        httpClientProvider.dispose()
        val client2 = httpClientProvider.getClient()
        assertNotNull("Client after dispose should be non-null", client2)
        client2.close()
    }

    fun testGetClientCachesSameConfig() {
        val client1 = httpClientProvider.getClient(httpClient = HttpClientType.APACHE.value, httpTimeOut = 5, unsafeSsl = false)
        assertNotNull("First client should be non-null", client1)
        val client2 = httpClientProvider.getClient(httpClient = HttpClientType.APACHE.value, httpTimeOut = 5, unsafeSsl = false)
        assertNotNull("Second client should be non-null", client2)
    }

    fun testGetInstanceReturnsSameProvider() {
        val provider1 = HttpClientProvider.getInstance(project)
        val provider2 = HttpClientProvider.getInstance(project)
        assertSame("getInstance should return same provider", provider1, provider2)
    }
}
