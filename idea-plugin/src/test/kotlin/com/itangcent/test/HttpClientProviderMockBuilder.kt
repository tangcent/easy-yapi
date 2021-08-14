package com.itangcent.test

import com.itangcent.common.kit.equalIgnoreCase
import com.itangcent.common.utils.mapToTypedArray
import com.itangcent.http.ApacheHttpClient
import com.itangcent.http.NOOP_HOST_NAME_VERIFIER
import com.itangcent.http.SSLSF
import com.itangcent.suv.http.HttpClientProvider
import com.itangcent.utils.and
import com.itangcent.utils.then
import org.apache.http.*
import org.apache.http.client.HttpClient
import org.apache.http.client.ResponseHandler
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.config.SocketConfig
import org.apache.http.conn.ClientConnectionManager
import org.apache.http.entity.BasicHttpEntity
import org.apache.http.entity.ContentType
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.message.BasicHeader
import org.apache.http.message.BasicHttpResponse
import org.apache.http.message.BasicStatusLine
import org.apache.http.params.HttpParams
import org.apache.http.protocol.HttpContext
import java.util.concurrent.atomic.AtomicInteger

class HttpClientProviderMockBuilder : RequestStub {

    private val handles: ArrayList<RequestHandle> = ArrayList()

    private var before: ((HttpUriRequest) -> Unit)? = null

    private var after: ((HttpUriRequest, HttpResponse?, Throwable?) -> Unit)? = null

    override fun url(url: String): CallStub {
        return call().url(url)
    }

    override fun method(method: String): CallStub {
        return call().method(method)
    }

    override fun withHeader(header: String): CallStub {
        return call().withHeader(header)
    }

    override fun withHeader(header: String, value: String): CallStub {
        return call().withHeader(header, value)
    }

    override fun contentType(contentType: String): CallStub {
        return call().contentType(contentType)
    }

    fun call(): CallStub {
        return CallStub(this) { true }
    }

    fun notFound(): ResponseStub {
        return CallStub(this, { true }, NOT_FOUND_PRIORITY)
    }

    fun beforeCall(action: (HttpUriRequest) -> Unit): HttpClientProviderMockBuilder {
        this.before = if (this.before == null) {
            action
        } else {
            this.before!!.then(action)
        }
        return this;
    }

    fun afterCall(action: (HttpUriRequest, HttpResponse?, Throwable?) -> Unit): HttpClientProviderMockBuilder {
        this.after = if (this.after == null) {
            action
        } else {
            this.after!!.then(action)
        }
        return this;
    }

    fun currentTotalLimit(limit: Int, error: Throwable): HttpClientProviderMockBuilder {
        val cnt = AtomicInteger(0)
        beforeCall {
            if (cnt.incrementAndGet() > limit) {
                throw error
            }
        }
        afterCall { _, _, _ ->
            cnt.decrementAndGet()
        }
        return this;
    }

    internal fun addHandle(requestHandle: RequestHandle) {
        this.handles.add(requestHandle)
    }

    fun build(): HttpClientProvider {
        val httpClient: HttpClient = MockHttpClient(
            handles.sortedBy { it.priority },
            before, after
        )
        return MockHttpClientProvider(ApacheHttpClient(httpClient))
    }

    companion object {
        fun builder(): HttpClientProviderMockBuilder {
            return HttpClientProviderMockBuilder()
        }
    }
}

class RequestHandle(
    val requestMatcher: (HttpUriRequest) -> Boolean,
    val response: () -> HttpResponse, priority: Int?
) {
    val priority: Int

    init {
        this.priority = priority ?: priorityBuilder.getAndIncrement()
    }

    companion object {
        val priorityBuilder = AtomicInteger()
    }
}

class MockHttpClient(
    private val handles: List<RequestHandle>,
    private val before: ((HttpUriRequest) -> Unit)?,
    private val after: ((HttpUriRequest, HttpResponse?, Throwable?) -> Unit)?
) : HttpClient {

    private val delegate: HttpClient by lazy {
        HttpClients.custom()
            .setConnectionManager(PoolingHttpClientConnectionManager().also {
                it.maxTotal = 50
                it.defaultMaxPerRoute = 20
            })
            .setDefaultSocketConfig(
                SocketConfig.custom()
                    .setSoTimeout(30 * 1000)
                    .build()
            )
            .setDefaultRequestConfig(
                RequestConfig.custom()
                    .setConnectTimeout(30 * 1000)
                    .setConnectionRequestTimeout(30 * 1000)
                    .setSocketTimeout(30 * 1000)
                    .setCookieSpec(CookieSpecs.STANDARD).build()
            )
            .setSSLHostnameVerifier(NOOP_HOST_NAME_VERIFIER)
            .setSSLSocketFactory(SSLSF)
            .build()
    }

    //region mock func
    override fun getParams(): HttpParams? {
        return null
    }

    override fun getConnectionManager(): ClientConnectionManager? {
        return null
    }

    override fun execute(request: HttpUriRequest?): HttpResponse? {
        return request?.let { handleRequest(it) }
    }

    override fun execute(request: HttpUriRequest?, context: HttpContext?): HttpResponse? {
        return request?.let { handleRequest(it) }
    }

    override fun execute(target: HttpHost?, request: HttpRequest?): HttpResponse? {
        TODO("Not yet implemented")
    }

    override fun execute(target: HttpHost?, request: HttpRequest?, context: HttpContext?): HttpResponse? {
        TODO("Not yet implemented")
    }

    override fun <T : Any?> execute(request: HttpUriRequest?, responseHandler: ResponseHandler<out T>?): T? {
        return responseHandler?.handleResponse(request?.let { handleRequest(it) })
    }

    override fun <T : Any?> execute(
        request: HttpUriRequest?,
        responseHandler: ResponseHandler<out T>?,
        context: HttpContext?
    ): T? {
        return responseHandler?.handleResponse(request?.let { handleRequest(it) })
    }

    override fun <T : Any?> execute(
        target: HttpHost?,
        request: HttpRequest?,
        responseHandler: ResponseHandler<out T>?
    ): T {
        TODO("Not yet implemented")
    }

    override fun <T : Any?> execute(
        target: HttpHost?,
        request: HttpRequest?,
        responseHandler: ResponseHandler<out T>?,
        context: HttpContext?
    ): T {
        TODO("Not yet implemented")
    }

    //endregion

    private fun handleRequest(request: HttpUriRequest): HttpResponse {
        for (handle in handles) {
            if (handle.requestMatcher(request)) {
                val response: HttpResponse
                try {
                    before?.let { it(request) }
                    response = handle.response()
                    after?.let { it(request, response, null) }
                    return response
                } catch (e: Throwable) {
                    after?.let { it(request, null, e) }
                    throw e
                }
            }
        }
        return delegate.execute(request)
    }
}

class MockHttpClientProvider(private val httpClient: com.itangcent.http.HttpClient) : HttpClientProvider {
    override fun getHttpClient(): com.itangcent.http.HttpClient {
        return httpClient
    }
}

interface RequestStub {

    fun method(method: String): CallStub

    fun url(url: String): CallStub

    fun withHeader(header: String): CallStub

    fun withHeader(header: String, value: String): CallStub

    fun contentType(contentType: String): CallStub
}

interface ResponseStub {

    fun response(
        content: String? = null,
        contentByte: ByteArray? = null,
        responseCode: Int = 200,
        contentType: ContentType = ContentType.APPLICATION_JSON,
        headers: Array<Pair<String, String>>? = null,
        elapse: Long? = null
    ): HttpClientProviderMockBuilder

    fun failed(e: Throwable, elapse: Long? = null): HttpClientProviderMockBuilder
}

fun ResponseStub.response404(): HttpClientProviderMockBuilder {
    return this.response(responseCode = 404)
}

class CallStub : RequestStub, ResponseStub {

    private val builder: HttpClientProviderMockBuilder
    private var requestMatcher: (HttpUriRequest) -> Boolean
    private val priority: Int?

    internal constructor(builder: HttpClientProviderMockBuilder, requestMatcher: (HttpUriRequest) -> Boolean) {
        this.builder = builder
        this.requestMatcher = requestMatcher
        this.priority = null
    }

    internal constructor(
        builder: HttpClientProviderMockBuilder,
        requestMatcher: (HttpUriRequest) -> Boolean,
        priority: Int
    ) {
        this.builder = builder
        this.requestMatcher = requestMatcher
        this.priority = priority
    }

    override fun url(url: String): CallStub {
        this.requestMatcher = this.requestMatcher.and { it.uri.toString() == url }
        return this
    }

    override fun method(method: String): CallStub {
        this.requestMatcher = this.requestMatcher.and { it.method.equalIgnoreCase(method) }
        return this
    }

    override fun withHeader(header: String): CallStub {
        this.requestMatcher = this.requestMatcher.and { it.containsHeader(header) }
        return this
    }

    override fun withHeader(header: String, value: String): CallStub {
        this.requestMatcher = this.requestMatcher.and { request ->
            request.getHeaders(header).firstOrNull { it.value.equalIgnoreCase(value) } != null
        }
        return this
    }

    override fun contentType(contentType: String): CallStub {
        return this.withHeader("Content-type", contentType)
    }

    override fun failed(
        e: Throwable,
        elapse: Long?
    ): HttpClientProviderMockBuilder {
        builder.addHandle(RequestHandle(this.requestMatcher, {
            elapse?.let { Thread.sleep(elapse) }
            throw e
        }, priority))
        return builder
    }

    override fun response(
        content: String?,
        contentByte: ByteArray?,
        responseCode: Int,
        contentType: ContentType,
        headers: Array<Pair<String, String>>?,
        elapse: Long?
    ): HttpClientProviderMockBuilder {
        builder.addHandle(RequestHandle(this.requestMatcher, {
            elapse?.let { Thread.sleep(elapse) }
            buildHttpResponse(content, contentByte, responseCode, headers, contentType)
        }, priority))
        return builder
    }

    private fun buildHttpResponse(
        content: String?,
        contentByte: ByteArray?,
        responseCode: Int,
        headers: Array<Pair<String, String>>?,
        contentType: ContentType
    ): BasicHttpResponse {
        //build response
        val httpEntity = BasicHttpEntity()
        httpEntity.content = content?.byteInputStream(Charsets.UTF_8)
            ?: contentByte?.inputStream()
                    ?: ByteArray(0).inputStream()
        val statusLine: StatusLine = BasicStatusLine(HttpVersion.HTTP_1_0, responseCode, "")
        val httpResponse = BasicHttpResponse(statusLine)
        httpResponse.entity = httpEntity
        headers?.mapToTypedArray { BasicHeader(it.first, it.second) }
            ?.let { httpResponse.setHeaders(it) }
        httpResponse.setHeader("Content-type", contentType.toString())
        return httpResponse
    }
}

const val NOT_FOUND_PRIORITY = Int.MAX_VALUE