package com.itangcent.suv.http

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.annotation.script.ScriptIgnore
import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.http.*
import com.itangcent.idea.plugin.api.export.core.ClassExportRuleKeys
import com.itangcent.idea.plugin.rule.SuvRuleContext
import com.itangcent.idea.plugin.settings.helper.HttpSettingsHelper
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.logger.Logger
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.config.SocketConfig
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

@Singleton
class ConfigurableHttpClientProvider : AbstractHttpClientProvider() {

    @Inject(optional = true)
    protected val httpSettingsHelper: HttpSettingsHelper? = null

    @Inject(optional = true)
    protected val configReader: ConfigReader? = null

    @Inject(optional = true)
    protected val ruleComputer: RuleComputer? = null

    @Inject
    protected val logger: Logger? = null

    override fun buildHttpClient(): HttpClient {
        val httpClientBuilder = HttpClients.custom()

        val config = readHttpConfig()

        httpClientBuilder
                .setConnectionManager(PoolingHttpClientConnectionManager().also {
                    it.maxTotal = 50
                    it.defaultMaxPerRoute = 20
                })
                .setDefaultSocketConfig(SocketConfig.custom()
                        .setSoTimeout(config.timeOut)
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(config.timeOut)
                        .setConnectionRequestTimeout(config.timeOut)
                        .setSocketTimeout(config.timeOut)
                        .setCookieSpec(CookieSpecs.STANDARD).build())
                .setSSLHostnameVerifier(NOOP_HOST_NAME_VERIFIER)
                .setSSLSocketFactory(SSLSF)

        return HttpClientWrapper(ApacheHttpClient(httpClientBuilder.build()))
    }

    private fun readHttpConfig(): HttpConfig {
        val httpConfig = HttpConfig()

        httpSettingsHelper?.let {
            httpConfig.timeOut = it.httpTimeOut(TimeUnit.MILLISECONDS)
        }

        if (configReader != null) {
            try {
                configReader.first("http.timeOut")?.toLong()
                        ?.let { httpConfig.timeOut = TimeUnit.SECONDS.toMillis(it).toInt() }
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

        /**
         * Set the HTTP method to request
         * @return current request
         */
        override fun method(method: String): HttpRequest {
            httpRequest.method(method)
            return this
        }

        /**
         * Set the url to request
         * @return current request
         */
        override fun url(url: String): HttpRequest {
            httpRequest.url(url)
            return this
        }

        /**
         * Adds the given header to the request.
         * The order in which this header was added is preserved.
         *
         * @param header the header to add
         * @return current request
         */
        override fun header(header: HttpHeader): HttpRequest {
            httpRequest.header(header)
            return this
        }

        /**
         * Adds the given header to the request.
         * The order in which this header was added is preserved.
         * @param headerName the name of the header to add
         * @param headerValue the value of the header to add
         * @return current request
         */
        override fun header(headerName: String, headerValue: String?): HttpRequest {
            httpRequest.header(headerName, headerValue)
            return this
        }

        /**
         * Sets the header to the request overriding any
         * existing headers with same name.
         *
         * @param headerName the name of the header to set
         * @param headerValue the value of the header to set
         * @return current request
         */
        override fun setHeader(headerName: String, headerValue: String?): HttpRequest {
            httpRequest.setHeader(headerName, headerValue)
            return this
        }

        /**
         * Removes the given header by special name.
         *
         * @param headerName the name of the header to remove
         * @return current request
         */
        override fun removeHeaders(headerName: String): HttpRequest {
            httpRequest.removeHeaders(headerName)
            return this
        }

        /**
         * Removes the given header by special name and value.
         *
         * @param headerName the name of the header to remove
         * @param headerValue the value of the header to remove
         * @return current request
         */
        override fun removeHeader(headerName: String, headerValue: String?): HttpRequest {
            httpRequest.removeHeader(headerName, headerValue)
            return this
        }

        override fun query(name: String, value: String?): HttpRequest {
            httpRequest.query(name, value)
            return this
        }

        /**
         * Set the body to be sent with the request.
         */
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

        /**
         * Set the Content-Type header value.
         * Overriding existing headers with Content-Type.
         *
         * @return current request
         */
        override fun contentType(contentType: String): HttpRequest {
            httpRequest.contentType(contentType)
            return this
        }

        /**
         * Executes HTTP request
         *
         * @return  the response to the request
         */
        override fun call(): HttpResponse {
            val url = url() ?: throw IllegalArgumentException("url not be set")
            if (httpSettingsHelper != null
                    && !httpSettingsHelper.checkTrustUrl(url, false)) {
                logger?.warn("[access forbidden] call:$url")
                return EmptyHttpResponse(this)
            }
            var i = 0
            while (true) {
                val suvRuleContext = SuvRuleContext()
                suvRuleContext.setExt("request", this)
                ruleComputer!!.computer(ClassExportRuleKeys.HTTP_CLIENT_BEFORE_CALL, suvRuleContext, null)
                val response = DiscardAbleHttpResponse(httpRequest.call())
                suvRuleContext.setExt("response", response)
                ruleComputer.computer(ClassExportRuleKeys.HTTP_CLIENT_AFTER_CALL, suvRuleContext, null)
                if (response.isDiscarded() && i < 3) {
                    response.close()
                    ++i
                    continue
                }
                return response
            }
        }
    }

    class EmptyHttpResponse(private val request: HttpRequest) : HttpResponse {
        override fun code(): Int {
            return 404
        }

        override fun headers(): List<HttpHeader>? {
            return null
        }

        override fun headers(headerName: String): Array<String>? {
            return null
        }

        override fun string(): String? {
            return null
        }

        override fun string(charset: Charset): String? {
            return null
        }

        override fun stream(): InputStream {
            return ByteArrayInputStream(byteArrayOf())
        }

        override fun contentType(): String? {
            return null
        }

        override fun bytes(): ByteArray? {
            return null
        }

        override fun containsHeader(headerName: String): Boolean? {
            return false
        }

        override fun firstHeader(headerName: String): String? {
            return null
        }

        override fun lastHeader(headerName: String): String? {
            return null
        }

        override fun request(): HttpRequest {
            return this.request
        }

        override fun close() {
            //NOP
        }

    }

    @ScriptTypeName("response")
    class DiscardAbleHttpResponse(httpResponse: HttpResponse) : HttpResponse by httpResponse {

        private var discarded = false

        /**
         * Discard current response.
         * Recall the request.
         */
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