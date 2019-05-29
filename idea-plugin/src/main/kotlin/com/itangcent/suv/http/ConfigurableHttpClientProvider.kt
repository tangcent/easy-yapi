package com.itangcent.suv.http

import com.google.inject.Inject
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.intellij.config.ConfigReader
import org.apache.http.client.HttpClient
import org.apache.http.config.SocketConfig
import org.apache.http.impl.client.HttpClients
import java.util.concurrent.TimeUnit

class ConfigurableHttpClientProvider : AbstractHttpClientProvider() {

    @Inject(optional = true)
    val settingBinder: SettingBinder? = null

    @Inject(optional = true)
    val configReader: ConfigReader? = null

    override fun buildHttpClient(): HttpClient {
        val httpClientBuilder = HttpClients.custom()

        val config = readHttpConfig()

        httpClientBuilder.setDefaultSocketConfig(SocketConfig.custom()
                .setSoTimeout(TimeUnit.SECONDS.toMillis(config.timeOut.toLong()).toInt())
                .build())
        return httpClientBuilder.build()
    }

    private fun readHttpConfig(): HttpConfig {
        val httpConfig = HttpConfig()

        settingBinder?.read()?.let { setting ->
            setting.httpTimeOut?.let { httpConfig.timeOut = it }
        }


        if (configReader != null) {
            try {
                configReader.read("http.timeOut")?.toInt()
                        ?.let { httpConfig.timeOut = it }
            } catch (e: NumberFormatException) {
            }
        }

        return httpConfig
    }

    class HttpConfig {

        //default 40s
        var timeOut: Int = 40
    }
}