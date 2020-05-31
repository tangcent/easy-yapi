package com.itangcent.http

import com.itangcent.annotation.script.ScriptIgnore
import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.common.kit.toJson
import com.itangcent.common.logger.ILogger
import com.itangcent.common.spi.SpiUtils
import com.itangcent.common.utils.notNullOrEmpty
import org.apache.http.HttpEntity
import org.apache.http.NameValuePair
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.RequestBuilder
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.config.SocketConfig
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.BasicCookieStore
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.cookie.BasicClientCookie
import org.apache.http.impl.cookie.BasicClientCookie2
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import java.io.File
import java.io.FileNotFoundException
import java.util.*

@ScriptTypeName("httpClient")
open class ApacheHttpClient : HttpClient {

    private val apacheCookieStore: ApacheCookieStore

    private val httpClientContext = HttpClientContext.create()

    private val httpClient: org.apache.http.client.HttpClient

    constructor() {
        val basicCookieStore = BasicCookieStore()
        apacheCookieStore = ApacheCookieStore(basicCookieStore)
        httpClientContext!!.cookieStore = basicCookieStore
        httpClient = HttpClients.custom()
                .setDefaultSocketConfig(SocketConfig.custom()
                        .setSoTimeout(30 * 1000)
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(30 * 1000)
                        .setConnectionRequestTimeout(30 * 1000)
                        .setSocketTimeout(30 * 1000)
                        .build()).build()
    }

    constructor(httpClient: org.apache.http.client.HttpClient) {
        val basicCookieStore = BasicCookieStore()
        apacheCookieStore = ApacheCookieStore(basicCookieStore)
        httpClientContext!!.cookieStore = basicCookieStore
        this.httpClient = httpClient
    }

    override fun cookieStore(): CookieStore {
        return apacheCookieStore
    }

    override fun request(): HttpRequest {
        return ApacheHttpRequest(this)
    }

    /**
     * Executes HTTP request using the given request.
     *
     * @return  the response to the request
     */
    @ScriptIgnore
    open fun call(request: ApacheHttpRequest): HttpResponse {

        var url = request.url()!!
        val querys = request.querys()
        if (querys.notNullOrEmpty()) {
            val urlParams = querys!!.joinToString("&") { "${it.name()}=${it.value()}" }
            url = when {
                url.contains('?') -> "$url&$urlParams"
                else -> "$url?$urlParams"
            }
        }

        val requestBuilder = RequestBuilder.create(request.method())
                .setUri(url)

        request.headers()?.forEach {
            requestBuilder.addHeader(it.name(), it.value())
        }

        if (request.method().toUpperCase() != "GET") {

            var requestEntity: HttpEntity? = null
            if (request.params().notNullOrEmpty()) {
                if (request.contentType()?.startsWith("application/x-www-form-urlencoded") != true) {
                    if (request.contentType()?.startsWith("multipart/form-data") == true) {
                        val entityBuilder = MultipartEntityBuilder.create()
                        for (param in request.params()!!) {
                            if (param.type() == "file") {
                                val filePath = param.value()
                                if (filePath.isNullOrBlank()) {
                                    continue
                                }
                                val file = File(filePath)
                                if (!file.exists() || !file.isFile) {
                                    throw FileNotFoundException("[$filePath] not exist")
                                }
                                entityBuilder.addBinaryBody(param.name(), file)
                            } else {
                                entityBuilder.addTextBody(param.name(), param.value())
                            }
                        }
                        val boundary = com.itangcent.common.http.EntityUtils.generateBoundary()
                        entityBuilder.setBoundary(boundary)
                        //set boundary to header
                        requestBuilder.setHeader("Content-type", "multipart/form-data; boundary=$boundary")
                        requestEntity = entityBuilder.build()
                    }
                } else {
                    val nameValuePairs: ArrayList<NameValuePair> = ArrayList()
                    for (param in request.params()!!) {
                        nameValuePairs.add(BasicNameValuePair(param.name(), param.value()))
                    }
                    requestEntity = UrlEncodedFormEntity(nameValuePairs)
                }
            }
            if (request.body() != null) {
                if (requestEntity != null) {
                    SpiUtils.loadService(ILogger::class)?.warn("The request with a body should not set content-type:${request.contentType()}")
                }
                requestEntity = StringEntity(request.body().toJson(),
                        ContentType.APPLICATION_JSON)
            }
            if (requestEntity != null) {
                requestBuilder.entity = requestEntity
            }
        }
        val httpRequest = requestBuilder.build()
        val httpResponse = httpClient.execute(httpRequest, httpClientContext)
        return ApacheHttpResponse(request, httpResponse)
    }
}

@ScriptTypeName("request")
class ApacheHttpRequest : AbstractHttpRequest {

    private val apacheHttpClient: ApacheHttpClient

    constructor(apacheHttpClient: ApacheHttpClient) : super() {
        this.apacheHttpClient = apacheHttpClient
    }


    /**
     * Executes HTTP request using the [apacheHttpClient].
     *
     * @return  the response to the request
     */
    override fun call(): HttpResponse {
        return this.apacheHttpClient.call(this)
    }
}

fun HttpRequest.contentType(contentType: ContentType): HttpRequest {
    this.contentType(contentType.toString())
    return this
}

/**
 * The implement of [CookieStore] by [org.apache.http.client.CookieStore].
 */
@ScriptTypeName("cookieStore")
class ApacheCookieStore : CookieStore {

    private var cookieStore: org.apache.http.client.CookieStore

    constructor(cookieStore: org.apache.http.client.CookieStore) {
        this.cookieStore = cookieStore
    }

    /**
     * Adds an [Cookie], replacing any existing equivalent cookies.
     * If the given cookie has already expired it will not be added, but existing
     * values will still be removed.
     *
     * @param cookie the [Cookie] to be added
     */
    override fun addCookie(cookie: Cookie?) {
        cookie?.let { cookieStore.addCookie(it.asApacheCookie()) }
    }

    /**
     * Adds [Cookie]s to the store.
     * It will replacing any existing equivalent cookies.
     * The cookie which has already expired will not be added, but existing
     * values will still be removed.
     *
     * @param cookies the [Cookie]s to be added
     */
    override fun addCookies(cookies: Array<Cookie>?) {
        cookies?.mapNotNull { cookie -> cookie.asApacheCookie() }
                ?.forEach { cookieStore.addCookie(it) }
    }

    /**
     * Returns all cookies contained in this store.
     *
     * @return the list of all cookies which is unmodifiable
     */
    override fun cookies(): List<Cookie> {
        return cookieStore.cookies.map { ApacheCookie(it) }
    }

    /**
     * Clears all cookies.
     */
    override fun clear() {
        cookieStore.clear()
    }

    override fun newCookie(): MutableCookie {
        return BasicCookie()
    }
}

/**
 * The implement of [HttpResponse] by [org.apache.http.HttpResponse].
 */
@ScriptTypeName("response")
class ApacheHttpResponse(
        val request: HttpRequest,
        val response: org.apache.http.HttpResponse) : AbstractHttpResponse() {

    /**
     * Obtains the status of this response.
     *
     * @return  the status of the response, or {@code null} if not yet set
     */
    override fun code(): Int? {
        val statusLine = response.statusLine
        return statusLine.statusCode
    }

    /**
     * Cache all the headers of this [HttpResponse].
     */
    private var headers: List<HttpHeader>? = null

    /**
     * Returns all the headers of this [HttpResponse].
     * Headers are orderd in the sequence they will be sent over a connection.
     *
     * @return all the headers of this [HttpResponse]
     */
    override fun headers(): List<HttpHeader>? {
        if (headers == null) {
            synchronized(this)
            {
                if (headers == null) {
                    val headers: ArrayList<HttpHeader> = ArrayList()
                    for (header in response.allHeaders) {
                        headers.add(BasicHttpHeader(header.name, header.value))
                    }
                    this.headers = headers
                }
            }
        }
        return this.headers
    }

    /**
     * Cache the bytes message of this response.
     */
    private var bytes: ByteArray? = null

    /**
     * Obtains the bytes message of this response.
     *
     * @return  the response bytes, or
     *          {@code null} if there is none
     */
    override fun bytes(): ByteArray? {
        if (bytes == null) {
            synchronized(this)
            {
                if (bytes == null) {
                    val entity = response.entity
                    bytes = EntityUtils.toByteArray(entity)
                }
            }
        }
        return bytes!!
    }

    /**
     * The request which be called.
     */
    override fun request(): HttpRequest {
        return request
    }
}

/**
 * The implement of [Cookie] by [org.apache.http.cookie.Cookie].
 */
@ScriptTypeName("cookie")
class ApacheCookie : Cookie {
    private val cookie: org.apache.http.cookie.Cookie

    fun getWrapper(): org.apache.http.cookie.Cookie {
        return cookie
    }

    constructor(cookie: org.apache.http.cookie.Cookie) {
        this.cookie = cookie
    }

    override fun getName(): String? {
        return cookie.name
    }

    override fun getValue(): String? {
        return cookie.value
    }

    override fun getComment(): String? {
        return cookie.comment
    }

    override fun getCommentURL(): String? {
        return cookie.commentURL
    }

    override fun getExpiryDate(): Long? {
        return cookie.expiryDate?.time
    }

    override fun isPersistent(): Boolean {
        return cookie.isPersistent
    }

    override fun getDomain(): String? {
        return cookie.domain
    }

    override fun getPath(): String? {
        return cookie.path
    }

    override fun getPorts(): IntArray? {
        return cookie.ports
    }

    override fun isSecure(): Boolean {
        return cookie.isSecure
    }

    override fun getVersion(): Int? {
        return cookie.version
    }
}

fun Cookie.asApacheCookie(): org.apache.http.cookie.Cookie? {
    if (this is ApacheCookie) {
        return this.getWrapper()
    }
    val cookie =
            if (this.getPorts() == null || this.getCommentURL() == null) {
                BasicClientCookie(this.getName(), this.getValue())
            } else {
                BasicClientCookie2(this.getName(), this.getValue())
            }
    cookie.comment = this.getComment()
    cookie.domain = this.getDomain()
    cookie.path = this.getPath()
    this.getVersion()?.let { cookie.version = it }
    cookie.isSecure = this.isSecure()
    this.getExpiryDate()?.let { Date(it) }?.let { cookie.expiryDate = it }
    this.getPorts()?.let { (cookie as BasicClientCookie2).ports = it }
    this.getCommentURL()?.let { (cookie as BasicClientCookie2).commentURL = it }
    return cookie
}
