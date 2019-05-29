package com.itangcent.suv.http

import org.apache.http.client.HttpClient

interface HttpClientProvider {
    fun getHttpClient(): HttpClient
}
