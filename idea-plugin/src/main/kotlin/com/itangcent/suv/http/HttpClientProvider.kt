package com.itangcent.suv.http

import com.google.inject.ImplementedBy
import com.google.inject.ProvidedBy
import com.google.inject.Singleton
import com.itangcent.http.HttpClient
import com.itangcent.spi.SpiSingleBeanProvider


/**
 * Defines an interface for obtaining an instance of HttpClient.
 *
 * It is the responsibility of each implementation to determine whether to return
 * the same instance of HttpClient consistently or to create a new instance for each request.
 *
 * @author tangcent
 * @date 2024/05/08
 */
@ProvidedBy(HttpClientProviderProvider::class)
interface HttpClientProvider {

    /**
     * Retrieve an instance of HttpClient, either as a singleton or as a new instance per call.
     */
    fun getHttpClient(): HttpClient
}

@Singleton
class HttpClientProviderProvider : SpiSingleBeanProvider<HttpClientProvider>()