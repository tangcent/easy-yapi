package com.itangcent.suv.http

/**
 * Test case of [DefaultHttpClientProvider]
 */
internal class DefaultHttpClientProviderTest : HttpClientProviderTest() {

    override val httpClientProviderClass get() = DefaultHttpClientProvider::class
}