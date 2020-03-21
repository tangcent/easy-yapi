package com.itangcent.http

import com.itangcent.annotation.script.ScriptIgnore
import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.common.utils.append
import com.itangcent.common.kit.toJson
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

    @ScriptIgnore
    open fun call(request: ApacheHttpRequest): HttpResponse {

        var url = request.url()!!
        request.querys()?.let { params ->
            params.joinToString("&") { "${it.name()}=${it.value()}" }
        }?.let { url = url.append(it, "&")!! }

        val requestBuilder = RequestBuilder.create(request.method())
                .setUri(url)

        request.headers()?.forEach {
            requestBuilder.addHeader(it.name(), it.value())
        }

        if (request.method().toUpperCase() != "GET") {

            var requestEntity: HttpEntity? = null
            if (!request.params().isNullOrEmpty()) {

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

    override fun call(): HttpResponse {
        return this.apacheHttpClient.call(this)
    }
}

fun HttpRequest.contentType(contentType: ContentType): HttpRequest {
    this.contentType(contentType.toString())
    return this
}

@ScriptTypeName("cookieStore")
class ApacheCookieStore : CookieStore {

    private var cookieStore: org.apache.http.client.CookieStore

    constructor(cookieStore: org.apache.http.client.CookieStore) {
        this.cookieStore = cookieStore
    }

    override fun addCookie(cookie: Cookie?) {
        cookie?.let { cookieStore.addCookie(it.asApacheCookie()) }
    }

    override fun addCookies(cookies: Array<Cookie>?) {
        cookies?.mapNotNull { cookie -> cookie.asApacheCookie() }
                ?.forEach { cookieStore.addCookie(it) }
    }

    override fun cookies(): List<Cookie> {
        return cookieStore.cookies.map { CookieWrapper(it) }
    }

    override fun clear() {
        cookieStore.clear()
    }

    override fun newCookie(): MutableCookie {
        return BasicCookie()
    }
}

@ScriptTypeName("response")
class ApacheHttpResponse(
        val request: HttpRequest,
        val response: org.apache.http.HttpResponse) : AbstractHttpResponse() {

    override fun code(): Int? {
        val statusLine = response.statusLine
        return statusLine.statusCode
    }

    private var headers: List<HttpHeader>? = null


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

    private var bytes: ByteArray? = null

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

    override fun request(): HttpRequest {
        return request
    }
}

@ScriptTypeName("cookie")
class CookieWrapper : Cookie {
    val cookie: org.apache.http.cookie.Cookie

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
    if (this is CookieWrapper) {
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
