package com.itangcent.suv.http

import com.google.inject.Inject
import com.itangcent.http.ApacheHttpClient
import com.itangcent.http.HttpClient
import com.itangcent.http.NOOP_HOST_NAME_VERIFIER
import com.itangcent.http.SSLSF
import com.itangcent.idea.plugin.condition.ConditionOnSetting
import com.itangcent.idea.plugin.settings.helper.HttpSettingsHelper
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.config.SocketConfig
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import java.util.concurrent.TimeUnit

/**
 * An implementation of [HttpClient] using Apache HttpClient.
 * Provides an Apache HttpClient based on configuration settings.
 *
 * @author tangcent
 * @date 2024/05/08
 */
@ConditionOnSetting("httpClient", havingValue = "Apache")
open class ApacheHttpClientProvider : AbstractHttpClientProvider() {

    @Inject
    protected lateinit var httpSettingsHelper: HttpSettingsHelper

    override fun buildHttpClient(): HttpClient {
        var httpClientBuilder = HttpClients.custom()

        // Initialize HttpClient with timeout settings.
        val timeOutInMills = httpSettingsHelper.httpTimeOut(TimeUnit.MILLISECONDS)
        httpClientBuilder = httpClientBuilder
            .setConnectionManager(PoolingHttpClientConnectionManager().also {
                it.maxTotal = 50
                it.defaultMaxPerRoute = 20
            })
            .setDefaultSocketConfig(
                SocketConfig.custom()
                    .setSoTimeout(timeOutInMills)
                    .build()
            )
            .setDefaultRequestConfig(
                RequestConfig.custom()
                    .setConnectTimeout(timeOutInMills)
                    .setConnectionRequestTimeout(timeOutInMills)
                    .setSocketTimeout(timeOutInMills)
                    .setCookieSpec(CookieSpecs.STANDARD).build()
            )

        // If unsafe SSL is allowed, configure HttpClient to trust all certificates.
        if (httpSettingsHelper.unsafeSsl()) {
            httpClientBuilder = httpClientBuilder.setSSLHostnameVerifier(NOOP_HOST_NAME_VERIFIER)
                .setSSLSocketFactory(SSLSF)
        }

        return ApacheHttpClient(httpClientBuilder.build())
    }
}