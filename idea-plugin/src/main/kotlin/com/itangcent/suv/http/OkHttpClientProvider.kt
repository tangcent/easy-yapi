package com.itangcent.suv.http

import com.google.inject.Inject
import com.itangcent.http.HttpClient
import com.itangcent.idea.plugin.condition.ConditionOnSetting
import com.itangcent.idea.plugin.settings.helper.HttpSettingsHelper
import org.apache.http.ssl.SSLContexts
import java.util.concurrent.TimeUnit

/**
 * An implementation of [HttpClient] using OkHttpClient from the OkHttp library.
 * Provides an OkHttpClient based on configuration settings.
 *
 * @author tangcent
 * @date 2024/05/08
 */
@ConditionOnSetting("httpClient", havingValue = "Okhttp")
open class OkHttpClientProvider : AbstractHttpClientProvider() {

    @Inject
    protected lateinit var httpSettingsHelper: HttpSettingsHelper

    override fun buildHttpClient(): HttpClient {
        // Initialize OkHttpClient with timeout settings.
        val timeOutInMills = httpSettingsHelper.httpTimeOut(TimeUnit.MILLISECONDS).toLong()
        val builder = okhttp3.OkHttpClient.Builder()
            .connectTimeout(timeOutInMills, TimeUnit.MILLISECONDS)
            .readTimeout(timeOutInMills, TimeUnit.MILLISECONDS)
            .writeTimeout(timeOutInMills, TimeUnit.MILLISECONDS)

        // If unsafe SSL is allowed, configure OkHttpClient to trust all certificates.
        if (httpSettingsHelper.unsafeSsl()) {
            builder.hostnameVerifier { _, _ -> true }
            val trustAllCert = object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<out java.security.cert.X509Certificate>?,
                    authType: String?
                ) {
                }

                override fun checkServerTrusted(
                    chain: Array<out java.security.cert.X509Certificate>?,
                    authType: String?
                ) {
                }

                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> {
                    return arrayOf()
                }
            }
            builder.sslSocketFactory(SSLContexts.createSystemDefault().socketFactory, trustAllCert)
        }

        return OkHttpClient(builder)
    }
}