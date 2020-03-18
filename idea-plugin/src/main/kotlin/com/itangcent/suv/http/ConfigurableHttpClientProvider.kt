package com.itangcent.suv.http

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.annotation.script.ScriptIgnore
import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.http.*
import com.itangcent.idea.plugin.api.export.ClassExportRuleKeys
import com.itangcent.idea.plugin.rule.SuvRuleContext
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.rule.RuleComputer
import org.apache.http.client.config.RequestConfig
import org.apache.http.config.SocketConfig
import org.apache.http.impl.client.HttpClients

@Singleton
class ConfigurableHttpClientProvider : AbstractHttpClientProvider() {

    @Inject(optional = true)
    val settingBinder: SettingBinder? = null

    @Inject(optional = true)
    val configReader: ConfigReader? = null

    @Inject(optional = true)
    val ruleComputer: RuleComputer? = null

    override fun buildHttpClient(): HttpClient {
        val httpClientBuilder = HttpClients.custom()

        val config = readHttpConfig()

        httpClientBuilder
                .setDefaultSocketConfig(SocketConfig.custom()
                        .setSoTimeout(config.timeOut * 1000)
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(config.timeOut * 1000)
                        .setConnectionRequestTimeout(config.timeOut * 1000)
                        .setSocketTimeout(config.timeOut * 1000)
                        .build())

        return HttpClientWrapper(ApacheHttpClient(httpClientBuilder.build()))
    }

    private fun readHttpConfig(): HttpConfig {
        val httpConfig = HttpConfig()

        settingBinder?.read()?.let { setting ->
            httpConfig.timeOut = setting.httpTimeOut
        }


        if (configReader != null) {
            try {
                configReader.first("http.timeOut")?.toInt()
                        ?.let { httpConfig.timeOut = it }
            } catch (e: NumberFormatException) {
            }
        }

        return httpConfig
    }

    @ScriptTypeName("httpClient")
    private inner class HttpClientWrapper(private val httpClient: HttpClient) : HttpClient {

        override fun cookieStore(): CookieStore {
            return httpClient.cookieStore()
        }

        override fun request(): HttpRequest {
            return HttpRequestWrapper(httpClient.request())
        }
    }

    @ScriptTypeName("request")
    private inner class HttpRequestWrapper(private val httpRequest: HttpRequest) : HttpRequest by httpRequest {
        override fun method(method: String): HttpRequest {
            httpRequest.method(method)
            return this
        }

        override fun url(url: String): HttpRequest {
            httpRequest.url(url)
            return this
        }

        override fun header(header: HttpHeader): HttpRequest {
            httpRequest.header(header)
            return this
        }

        override fun header(headerName: String, headerValue: String?): HttpRequest {
            httpRequest.header(headerName, headerValue)
            return this
        }

        override fun setHeader(headerName: String, headerValue: String?): HttpRequest {
            httpRequest.setHeader(headerName, headerValue)
            return this
        }

        override fun removeHeaders(headerName: String): HttpRequest {
            httpRequest.removeHeaders(headerName)
            return this
        }

        override fun removeHeader(headerName: String, headerValue: String?): HttpRequest {
            httpRequest.removeHeader(headerName, headerValue)
            return this
        }

        override fun query(name: String, value: String?): HttpRequest {
            httpRequest.query(name, value)
            return this
        }

        override fun body(body: Any?): HttpRequest {
            httpRequest.body(body)
            return this
        }

        override fun param(paramName: String, paramValue: String?): HttpRequest {
            httpRequest.param(paramName, paramValue)
            return this
        }

        override fun fileParam(paramName: String, filePath: String?): HttpRequest {
            httpRequest.fileParam(paramName, filePath)
            return this
        }

        override fun contentType(contentType: String): HttpRequest {
            httpRequest.contentType(contentType)
            return this
        }

        override fun call(): HttpResponse {
            var i = 0
            while (true) {
                val suvRuleContext = SuvRuleContext()
                suvRuleContext.setExt("request", this)
                ruleComputer!!.computer(ClassExportRuleKeys.HTTP_CLIENT_BEFORE_CALL, suvRuleContext, null)
                val response = DiscardableHttpResponse(httpRequest.call())
                suvRuleContext.setExt("response", response)
                ruleComputer.computer(ClassExportRuleKeys.HTTP_CLIENT_AFTER_CALL, suvRuleContext, null)
                if (response.isDiscarded() && i < 3) {
                    ++i
                    continue
                }
                return response
            }
        }
    }

    @ScriptTypeName("response")
    class DiscardableHttpResponse(httpResponse: HttpResponse) : HttpResponse by httpResponse {

        var discarded = false

        fun discard() {
            this.discarded = true
        }

        @ScriptIgnore
        fun isDiscarded(): Boolean {
            return this.discarded
        }
    }

    class HttpConfig {

        //default 10s
        var timeOut: Int = defaultHttpTimeOut
    }

    companion object {
        const val defaultHttpTimeOut: Int = 10
    }


}