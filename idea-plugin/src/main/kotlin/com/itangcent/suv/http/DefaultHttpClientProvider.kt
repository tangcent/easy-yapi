package com.itangcent.suv.http

import org.apache.http.client.HttpClient
import org.apache.http.impl.client.HttpClients

class DefaultHttpClientProvider : AbstractHttpClientProvider() {
    override fun buildHttpClient(): HttpClient {
        return HttpClients.createDefault()
    }
}