package com.itangcent.suv.http

/**
 * Test case of [ConfigurableHttpClientProvider]
 */
internal class ConfigurableHttpClientProviderTest : HttpClientProviderTest() {

    override val httpClientProviderClass get() = ConfigurableHttpClientProvider::class

    override fun customConfig(): String {
        return "http.call.before=groovy:logger.info(\"call:\"+request.url())\nhttp.call.after=groovy:logger.info(\"response:\"+response.string())\nhttp.timeOut=3"
    }
}