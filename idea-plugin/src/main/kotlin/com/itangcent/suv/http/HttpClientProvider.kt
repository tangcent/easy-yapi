package com.itangcent.suv.http

import com.google.inject.ImplementedBy
import com.itangcent.http.HttpClient


@ImplementedBy(DefaultHttpClientProvider::class)
interface HttpClientProvider {

    fun getHttpClient(): HttpClient
}
