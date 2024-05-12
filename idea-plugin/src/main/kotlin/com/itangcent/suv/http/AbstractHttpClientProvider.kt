package com.itangcent.suv.http

import com.itangcent.http.HttpClient

/**
 * Abstract base class for creating and providing an HttpClient instance.
 * This class implements the HttpClientProvider interface and uses a singleton pattern to
 * ensure that a single, shared instance of HttpClient is provided across the entire application.
 */
abstract class AbstractHttpClientProvider : HttpClientProvider {

    /**
     * The HttpClient instance that will be provided by this provider.
     * This instance is created lazily and is shared across the entire application.
     */
    protected val httpClientInstance: HttpClient by lazy {
        buildHttpClient()
    }

    /**
     * Retrieve the shared instance of HttpClient.
     */
    override fun getHttpClient(): HttpClient = httpClientInstance

    /**
     * Create and configure an instance of HttpClient.
     */
    abstract fun buildHttpClient(): HttpClient
}