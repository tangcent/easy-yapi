package com.itangcent.suv.http

import com.google.inject.ImplementedBy
import com.itangcent.http.HttpClient


/**
 * Defines an interface for obtaining an instance of HttpClient.
 *
 * It is the responsibility of each implementation to determine whether to return
 * the same instance of HttpClient consistently or to create a new instance for each request.
 *
 * @author tangcent
 * @date 2024/05/08
 */
@ImplementedBy(DefaultHttpClientProvider::class)
interface HttpClientProvider {

    /**
     * Retrieve an instance of HttpClient, either as a singleton or as a new instance per call.
     */
    fun getHttpClient(): HttpClient
}
