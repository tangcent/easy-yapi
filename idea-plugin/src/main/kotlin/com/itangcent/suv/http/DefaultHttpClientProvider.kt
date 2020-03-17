package com.itangcent.suv.http

import com.google.inject.Singleton
import com.itangcent.http.ApacheHttpClient
import com.itangcent.http.HttpClient


@Singleton
class DefaultHttpClientProvider : AbstractHttpClientProvider() {

    override fun buildHttpClient(): HttpClient {
        return ApacheHttpClient()
    }
}