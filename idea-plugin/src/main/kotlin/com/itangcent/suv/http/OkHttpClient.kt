package com.itangcent.suv.http

import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.common.utils.GsonUtils
import com.itangcent.http.*
import com.itangcent.http.Cookie
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit

/**
 * Provides a concrete implementation of HttpClient using OkHttpClient from the OkHttp library.
 *
 * @author tangcent
 * @date 2024/04/27
 */
@ScriptTypeName("httpClient")
class OkHttpClient : HttpClient {

    private val cookieStore: OkHttpCookieStore = OkHttpCookieStore()

    private val client: okhttp3.OkHttpClient

    constructor(clientBuilder: okhttp3.OkHttpClient.Builder) {
        this.client = clientBuilder.cookieJar(cookieStore).build()
    }

    constructor() : this(
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
    )

    override fun cookieStore(): CookieStore {
        return cookieStore
    }

    override fun request(): HttpRequest {
        return OkHttpRequest(this)
    }

    /**
     * Handles the execution of OkHttpRequest and constructs an OkHttpResponse.
     */
    fun call(request: OkHttpRequest): HttpResponse {
        val builder = Request.Builder()

        // Handle URL and query parameters
        val httpUrlBuilder =
            request.url()?.toHttpUrlOrNull()?.newBuilder() ?: throw IllegalArgumentException("Invalid URL")
        request.querys()?.forEach { query ->
            val name = query.name()
            assert(name != null) { "Query parameter must have a name" }
            httpUrlBuilder.addQueryParameter(name!!, query.value())
        }
        builder.url(httpUrlBuilder.build())

        // Handle headers
        request.headers()?.forEach { header ->
            val name = header.name()
            assert(name != null) { "Header must have a name" }
            builder.addHeader(name!!, header.value() ?: "")
        }

        // Handle request body
        val requestBody = buildRequestBody(request)

        // Set request method and body
        builder.method(request.method(), requestBody)

        val call = client.newCall(builder.build())
        val response = call.execute()

        return OkHttpResponse(request, response)
    }

    private fun buildRequestBody(request: OkHttpRequest): RequestBody? {
        if (request.method().equals("GET", ignoreCase = true)) return null

        if (request.contentType()?.startsWith("application/x-www-form-urlencoded") == true) {
            val formBodyBuilder = FormBody.Builder()
            request.params()?.forEach { param ->
                formBodyBuilder.add(param.name() ?: "", param.value() ?: "")
            }
            return formBodyBuilder.build()
        }

        if (request.contentType()?.startsWith("multipart/form-data") == true) {
            val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            request.params()?.forEach { param ->
                if (param.type() == "file") {
                    if (param.value() == null) throw FileNotFoundException("file not found")
                    val file = File(param.value()!!)
                    if (!file.exists() || !file.isFile) {
                        throw FileNotFoundException("file ${file.absolutePath} not exists")
                    }
                    builder.addFormDataPart(
                        param.name() ?: "",
                        file.name,
                        file.asRequestBody(contentType = "application/octet-stream".toMediaType())
                    )
                } else {
                    builder.addFormDataPart(param.name() ?: "", param.value() ?: "")
                }
            }
            return builder.build()
        }

        val body = request.body() ?: return null
        return (when (body) {
            is String -> body
            else -> GsonUtils.toJson(body)
        }).toRequestBody((request.contentType() ?: "application/json; charset=utf-8").toMediaType())
    }
}

/**
 * Implementation of CookieJar interface to support cookie management in OkHttp.
 */
class OkHttpCookieStore : CookieJar, CookieStore {
    private val cookieStore = mutableMapOf<String, List<okhttp3.Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<okhttp3.Cookie>) {
        cookieStore[url.host] = cookies.filter { !it.isExpired() }
    }

    override fun loadForRequest(url: HttpUrl): List<okhttp3.Cookie> {
        val cookies = cookieStore[url.host] ?: return emptyList()
        return cookies.asSequence()
            .filter { !it.isExpired() }
            .filter { it.matches(url) }
            .toList()
    }

    override fun addCookie(cookie: Cookie?) {
        if (cookie == null || cookie.isExpired()) {
            return
        }
        val domain = cookie.getDomain() ?: return
        val existingCookies = cookieStore.getOrDefault(domain, emptyList())
        cookieStore[domain] = existingCookies.toMutableList() + cookie.asOkHttpCookie()

    }

    override fun addCookies(cookies: Array<Cookie>?) {
        cookies?.forEach { addCookie(it) }
    }

    override fun cookies(): List<Cookie> {
        return cookieStore.values.asSequence()
            .flatten()
            .filter { !it.isExpired() }
            .map { OkHttpCookie(it) }
            .toList()
    }

    override fun clear() {
        cookieStore.clear()
    }

    override fun newCookie(): MutableCookie {
        return BasicCookie()
    }
}

private fun okhttp3.Cookie.isExpired(): Boolean {
    return expiresAt < System.currentTimeMillis()
}

/**
 * A simple wrapper around the okhttp3.Cookie to adapt it to the custom [Cookie] interface.
 */
@ScriptTypeName("cookie")
class OkHttpCookie(private val cookie: okhttp3.Cookie) : Cookie {

    fun getWrapper(): okhttp3.Cookie {
        return cookie
    }

    override fun getName(): String {
        return cookie.name
    }

    override fun getValue(): String {
        return cookie.value
    }

    override fun getDomain(): String {
        return cookie.domain
    }

    override fun getPath(): String {
        return cookie.path
    }

    override fun getExpiryDate(): Long {
        return cookie.expiresAt
    }

    override fun isPersistent(): Boolean {
        return cookie.persistent
    }

    override fun isSecure(): Boolean {
        return cookie.secure
    }

    @Deprecated("For compatibility only")
    override fun getComment(): String? {
        // OkHttp's Cookie class does not support comments; return null or an empty string if needed.
        return null
    }

    @Deprecated("For compatibility only")
    override fun getCommentURL(): String? {
        // OkHttp's Cookie class does not support comment URLs; return null.
        return null
    }

    @Deprecated("For compatibility only")
    override fun getPorts(): IntArray? {
        // OkHttp's Cookie class does not support ports; return null.
        return null
    }

    @Deprecated("For compatibility only")
    override fun getVersion(): Int {
        // OkHttp's Cookie class does not explicitly handle version; typically version 1 (Netscape spec) is assumed.
        return 1
    }

    override fun toString(): String {
        return cookie.toString()
    }
}

// Converts a generic Cookie instance into an okhttp3.Cookie.
fun Cookie.asOkHttpCookie(): okhttp3.Cookie {
    if (this is OkHttpCookie) {
        return this.getWrapper()
    }

    // Build a new OkHttp Cookie from generic Cookie interface
    return okhttp3.Cookie.Builder().apply {
        name(this@asOkHttpCookie.getName() ?: throw IllegalArgumentException("Cookie name cannot be null"))
        value(this@asOkHttpCookie.getValue() ?: throw IllegalArgumentException("Cookie value cannot be null"))
        domain(this@asOkHttpCookie.getDomain() ?: throw IllegalArgumentException("Cookie domain cannot be null"))
        path(this@asOkHttpCookie.getPath() ?: "/")  // Default to root if path is not specified

        if (this@asOkHttpCookie.getExpiryDate() != null) {
            expiresAt(this@asOkHttpCookie.getExpiryDate()!!)
        } else {
            // If no expiry is set, use a far future date to mimic a non-expiring cookie
            expiresAt(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(365))  // Plus one year
        }

        if (this@asOkHttpCookie.isSecure()) {
            secure()
        }
    }.build()
}

/**
 * Represents an HTTP request specific to OkHttp implementation.
 * Handles the delegation of the call to the OkHttpClient instance.
 */
@ScriptTypeName("request")
class OkHttpRequest(private val client: OkHttpClient) : AbstractHttpRequest() {
    override fun call(): HttpResponse {
        return client.call(this)
    }
}

/**
 * Represents an HTTP response from OkHttp.
 */
@ScriptTypeName("response")
class OkHttpResponse(
    private val request: HttpRequest,
    private val response: Response
) : AbstractHttpResponse(), AutoCloseable {

    /**
     * Obtains the status code of the HTTP response.
     *
     * @return the HTTP status code
     */
    override fun code(): Int {
        return response.code
    }

    /**
     * Obtains all headers of the HTTP response.
     *
     * @return a list of headers (name-value pairs)
     */
    override fun headers(): List<HttpHeader> {
        return response.headers.names()
            .flatMap { name -> response.headers(name).map { value -> BasicHttpHeader(name, value) } }
    }

    /**
     * the bytes message of this response.
     */
    private val bodyBytes: ByteArray? by lazy {
        response.body?.bytes()
    }

    /**
     * Obtains the byte array of the response body if available.
     *
     * @return the byte array of the response body, or null if no body is available
     */
    override fun bytes(): ByteArray? {
        return bodyBytes
    }

    override fun request(): HttpRequest {
        return request
    }

    /**
     * Closes the response to free resources.
     */
    override fun close() {
        response.close()
    }
}