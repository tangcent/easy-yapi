package com.itangcent.suv.http

import com.itangcent.http.HttpClient


abstract class AbstractHttpClientProvider : HttpClientProvider {

    protected var httpClientInstance: HttpClient? = null

    override fun getHttpClient(): HttpClient {
        if (httpClientInstance == null) {
            synchronized(this)
            {
                if (httpClientInstance == null) {
                    httpClientInstance = buildHttpClient()
                }
            }
        }
        return httpClientInstance!!
    }

    abstract fun buildHttpClient(): HttpClient
}