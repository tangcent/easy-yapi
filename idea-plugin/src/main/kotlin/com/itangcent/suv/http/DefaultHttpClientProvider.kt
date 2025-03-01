package com.itangcent.suv.http

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.http.HttpClient
import com.itangcent.intellij.context.ActionContext
import com.itangcent.spi.SpiCompositeLoader


/**
 * The default implementation of the [HttpClientProvider] interface
 * which automatically loads an implementation of the HttpClientProvider interface using the service provider interface (SPI) mechanism.
 */
@Singleton
open class DefaultHttpClientProvider : AbstractHttpClientProvider() {

    @Inject
    private lateinit var actionContext: ActionContext

    private val httpClientProvider: HttpClientProvider by lazy {
        SpiCompositeLoader.load<HttpClientProvider>(actionContext).firstOrNull()
            ?: actionContext.instance(ApacheHttpClientProvider::class)
    }

    override fun buildHttpClient(): HttpClient {
        return httpClientProvider.getHttpClient()
    }
}