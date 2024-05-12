package com.itangcent.suv.http

import com.google.inject.matcher.Matchers
import com.itangcent.annotation.script.ScriptIgnore
import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.common.spi.SetupAble
import com.itangcent.http.*
import com.itangcent.idea.plugin.api.export.core.ClassExportRuleKeys
import com.itangcent.idea.plugin.rule.SuvRuleContext
import com.itangcent.idea.plugin.settings.helper.HttpSettingsHelper
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset

/**
 * A support class for integrating [HttpClientProvider]s with scripting functionalities.
 * This class setups method interceptors on HTTP client providers to augment them with additional
 * scripting capabilities, monitoring, and conditional execution of HTTP requests based on script annotations.
 * It is responsible for setting up interception of HTTP client methods to apply custom logic before
 * or after HTTP requests are executed.
 *
 * @author tangcent
 * @date 2024/05/08
 */
class HttpClientScriptInterceptorSupport : SetupAble {
    /**
     * Initializes and adds default injections to intercept [HttpClientProvider] methods,
     * applying custom interceptor logic defined in [HttpClientScriptInterceptor].
     */
    override fun init() {
        ActionContext.addDefaultInject { builder ->
            builder.bindInterceptor(
                Matchers.subclassesOf(HttpClientProvider::class.java),
                Matchers.any(),
                HttpClientScriptInterceptor
            )
        }
    }
}

/**
 * A method interceptor for [HttpClientProvider] which wraps returned HttpClient instances in a custom wrapper
 * to augment them with additional functional behaviors.
 */
object HttpClientScriptInterceptor : MethodInterceptor {

    private val logger: Logger = ActionContext.local()
    private val ruleComputer: RuleComputer = ActionContext.local()
    private val httpSettingsHelper: HttpSettingsHelper = ActionContext.local()

    /**
     * Intercepts method invocations on [HttpClientProvider]s, wrapping returned HttpClient objects
     * if they are not already wrapped.
     */
    override fun invoke(invocation: MethodInvocation): Any {
        if (invocation.method.returnType == HttpClient::class.java) {
            return (invocation.proceed() as HttpClient).wrap()
        }
        return invocation.proceed()
    }

    /**
     * Ensures that an HttpClient instance is wrapped with [HttpClientWrapper] to apply custom behaviors.
     *
     * @return A wrapped HttpClient instance.
     */
    private fun HttpClient.wrap(): HttpClient {
        if (this is HttpClientWrapper) {
            return this
        }
        return HttpClientWrapper(this)
    }

    /**
     * A wrapper class that implements the HttpClient interface and delegates to a wrapped HttpClient instance.
     */
    @ScriptTypeName("httpClient")
    internal class HttpClientWrapper(val delegate: HttpClient) : HttpClient {

        override fun cookieStore(): CookieStore {
            return delegate.cookieStore()
        }

        /**
         * Wraps the request in a custom HttpRequestWrapper implementation and delegates to the wrapped HttpClient instance.
         */
        override fun request(): HttpRequest {
            return HttpRequestWrapper(delegate.request())
        }
    }

    /**
     * A wrapper class that implements the HttpRequest interface and delegates to a wrapped HttpRequest instance.
     */
    @ScriptTypeName("request")
    private class HttpRequestWrapper(private val httpRequest: HttpRequest) : HttpRequest by httpRequest {

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
            if (!httpSettingsHelper.checkTrustUrl(url, false)) {
                logger.warn("[access forbidden] call:$url")
                return EmptyHttpResponse(this)
            }
            var i = 0
            while (true) {
                val suvRuleContext = SuvRuleContext()
                suvRuleContext.setExt("request", this)
                ruleComputer.computer(ClassExportRuleKeys.HTTP_CLIENT_BEFORE_CALL, suvRuleContext, null)
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

    /**
     * An implementation of the HttpResponse interface that returns empty or null values for all methods.
     */
    private class EmptyHttpResponse(private val request: HttpRequest) : HttpResponse {
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

        override fun containsHeader(headerName: String): Boolean {
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

    /**
     * A custom implementation of the HttpResponse interface that wraps a delegate HttpResponse instance and adds a
     * discard() method that can be used to discard the current response and recall the request.
     */
    @ScriptTypeName("response")
    private class DiscardAbleHttpResponse(httpResponse: HttpResponse) : HttpResponse by httpResponse {

        private var discarded = false

        /**
         * Discards the current response and returns a new HttpResponse instance for the original request.
         */
        fun discard() {
            this.discarded = true
        }

        @ScriptIgnore
        fun isDiscarded(): Boolean {
            return this.discarded
        }
    }
}