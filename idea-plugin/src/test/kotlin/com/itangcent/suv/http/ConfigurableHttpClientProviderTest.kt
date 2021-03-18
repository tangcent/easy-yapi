package com.itangcent.suv.http

import java.io.File

/**
 * Test case of [ConfigurableHttpClientProvider]
 */
internal class ConfigurableHttpClientProviderTest : HttpClientProviderTest() {

    override val httpClientProviderClass get() = ConfigurableHttpClientProvider::class

    override fun initConfig(file: File) {
        super.initConfig(file)
        file.writeText("http.call.before=groovy:logger.info(\"call:\"+request.url())\nhttp.call.after=groovy:logger.info(\"response:\"+response.string())\nhttp.timeOut=3", Charsets.UTF_8)
    }
}