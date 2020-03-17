package com.itangcent.http

import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.common.utils.KitUtils
import com.itangcent.common.utils.equalIgnoreCase
import org.apache.http.Consts
import org.apache.http.entity.ContentType
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset

@ScriptTypeName("cookieStore")
interface CookieStore {

    fun addCookie(cookie: Cookie?)

    fun addCookies(cookies: Array<Cookie>?)

    fun cookies(): List<Cookie>

    fun clear()

    fun newCookie(): MutableCookie
}

@ScriptTypeName("cookie")
interface Cookie {

    fun getName(): String?

    fun getValue(): String?

    fun getComment(): String?

    fun getCommentURL(): String?

    fun getExpiryDate(): Long?

    fun isPersistent(): Boolean

    fun getDomain(): String?

    fun getPath(): String?

    fun getPorts(): IntArray?

    fun isSecure(): Boolean

    fun getVersion(): Int?
}

interface MutableCookie : Cookie {

    fun setName(name: String?)

    fun setValue(value: String?)

    fun setComment(comment: String?)

    fun setCommentURL(commentURL: String?)

    fun setExpiryDate(expiryDate: Long?)

    fun setDomain(domain: String?)

    fun setPath(path: String?)

    fun setPorts(ports: IntArray?)

    fun setSecure(secure: Boolean)

    fun setVersion(version: Int?)
}

@ScriptTypeName("request")
interface HttpRequest {

    fun method(): String

    fun method(method: String): HttpRequest

    fun url(): String?

    fun url(url: String): HttpRequest

    //region header---------------------------------------------------------

    fun containsHeader(headerName: String): Boolean?
    fun firstHeader(headerName: String): String?
    fun lastHeader(headerName: String): String?
    fun headers(headerName: String): Array<String>?
    fun headers(): Array<HttpHeader>?
    fun header(header: HttpHeader): HttpRequest
    fun header(headerName: String, headerValue: String?): HttpRequest
    fun setHeader(headerName: String, headerValue: String?): HttpRequest
    fun removeHeaders(headerName: String): HttpRequest

    fun removeHeader(headerName: String, headerValue: String?): HttpRequest

    //endregion header---------------------------------------------------------
    fun query(name: String, value: String?): HttpRequest

    fun querys(): List<HttpParam>?
    fun body(body: Any?): HttpRequest
    fun body(): Any?
    fun param(paramName: String, paramValue: String?): HttpRequest
    fun fileParam(paramName: String, filePath: String?): HttpRequest
    fun params(): Array<HttpParam>?
    fun containsParam(paramName: String): Boolean?
    fun params(paramName: String): Array<HttpParam>?
    fun paramValues(paramName: String): Array<String>?
    fun firstParam(paramName: String): HttpParam?
    fun firstParamValue(paramName: String): String?
    fun lastParam(paramName: String): HttpParam?
    fun lastParamValue(paramName: String): String?
    fun contentType(): String?


    fun contentType(contentType: String): HttpRequest
    fun call(): HttpResponse
}

abstract class AbstractHttpRequest : HttpRequest {

    private var method: String = "GET"

    override fun method(): String {
        return this.method
    }

    override fun method(method: String): HttpRequest {
        this.method = method
        return this
    }

    private var url: String? = null

    override fun url(): String? {
        return this.url
    }

    override fun url(url: String): HttpRequest {
        this.url = url
        return this
    }

    //region header---------------------------------------------------------

    private var headers: List<HttpHeader>? = null

    override fun containsHeader(headerName: String): Boolean? {
        return headers?.any { it.name().equalIgnoreCase(headerName) } ?: false
    }

    override fun headers(headerName: String): Array<String>? {
        return headers
                ?.filter { it.name().equalIgnoreCase(headerName) }
                ?.map { it.value() }
                ?.filter { it != null }
                ?.map { it as String }
                ?.toTypedArray()
    }

    override fun firstHeader(headerName: String): String? {
        return headers
                ?.filter { it.name().equalIgnoreCase(headerName) }
                ?.map { it.value() }
                ?.first { it != null }
    }

    override fun lastHeader(headerName: String): String? {
        return headers
                ?.filter { it.name().equalIgnoreCase(headerName) }
                ?.map { it.value() }
                ?.last { it != null }
    }

    override fun headers(): Array<HttpHeader>? {
        return headers?.toTypedArray()
    }

    private fun tryInitHeaders(): MutableList<HttpHeader> {
        if (headers == null) {
            headers = ArrayList()
        }
        return (this.headers as MutableList<HttpHeader>?)!!
    }

    override fun header(header: HttpHeader): HttpRequest {
        tryInitHeaders().add(header)
        return this
    }

    override fun header(headerName: String, headerValue: String?): HttpRequest {
        tryInitHeaders().add(BasicHttpHeader(headerName, headerValue))
        return this
    }

    override fun setHeader(headerName: String, headerValue: String?): HttpRequest {
        removeHeaders(headerName)
        header(headerName, headerValue)
        return this
    }

    override fun removeHeaders(headerName: String): HttpRequest {
        tryInitHeaders().removeAll { it.name().equalIgnoreCase(headerName) }
        return this
    }

    override fun removeHeader(headerName: String, headerValue: String?): HttpRequest {
        tryInitHeaders().removeAll { it.name().equalIgnoreCase(headerName) && it.value() == headerValue }
        return this
    }

    override fun contentType(): String? {
        return this.firstHeader("content-type")
    }

    override fun contentType(contentType: String): HttpRequest {
        return this.header("Content-Type", contentType)
    }

    //endregion header---------------------------------------------------------

    private var querys: List<HttpParam>? = null

    private fun tryInitQuerys(): MutableList<HttpParam> {
        if (querys == null) {
            querys = ArrayList()
        }
        return (this.querys as MutableList<HttpParam>?)!!
    }

    override fun querys(): List<HttpParam>? {
        return querys
    }

    override fun query(name: String, value: String?): HttpRequest {
        tryInitQuerys().add(BasicHttpParam(name, value))
        return this
    }

    //region form params---------------------------------------------------------

    private var form: List<HttpParam>? = null

    private fun tryInitFormParams(): MutableList<HttpParam> {
        if (form == null) {
            form = ArrayList()
        }
        return (this.form as MutableList<HttpParam>?)!!
    }

    override fun param(paramName: String, paramValue: String?): HttpRequest {
        tryInitFormParams().add(BasicHttpParam(paramName, paramValue))
        return this
    }

    override fun fileParam(paramName: String, filePath: String?): HttpRequest {
        tryInitFormParams().add(BasicHttpParam(paramName, filePath, "file"))
        return this
    }

    override fun containsParam(paramName: String): Boolean? {
        return form?.any { it.name() == paramName } ?: false
    }

    override fun params(paramName: String): Array<HttpParam>? {
        return form
                ?.filter { it.name() == paramName }
                ?.toTypedArray()
    }

    override fun paramValues(paramName: String): Array<String>? {
        return form
                ?.filter { it.name() == paramName }
                ?.map { it.value() }
                ?.filter { it != null }
                ?.map { it as String }
                ?.toTypedArray()
    }

    override fun firstParam(paramName: String): HttpParam? {
        return form?.first { it.name() == paramName }
    }

    override fun firstParamValue(paramName: String): String? {
        return form
                ?.filter { it.name() == paramName }
                ?.map { it.value() }
                ?.first { it != null }
    }

    override fun lastParam(paramName: String): HttpParam? {
        return form?.last { it.name() == paramName }
    }

    override fun lastParamValue(paramName: String): String? {
        return form
                ?.filter { it.name() == paramName }
                ?.map { it.value() }
                ?.last { it != null }
    }

    override fun params(): Array<HttpParam>? {
        return form?.toTypedArray()
    }

    //endregion---------------------------------------------------------

    private var body: Any? = null

    override fun body(body: Any?): HttpRequest {
        this.body = body
        return this
    }

    override fun body(): Any? {
        return this.body
    }

}

@ScriptTypeName("response")
interface HttpResponse {

    fun code(): Int?

    fun headers(): List<HttpHeader>?

    fun string(): String?

    fun string(charset: Charset): String?

    fun stream(): InputStream

    fun contentType(): String?

    fun bytes(): ByteArray?

    fun containsHeader(headerName: String): Boolean?
    fun headers(headerName: String): Array<String>?
    fun firstHeader(headerName: String): String?
    fun lastHeader(headerName: String): String?

    fun request(): HttpRequest
}

fun HttpResponse.getHeaderFileName(): String? {
    val dispositionHeader = this.firstHeader("Content-Disposition")
    if (dispositionHeader.isNullOrBlank()) return null
    var fileName = dispositionHeader.substringAfter("filename=", "")
    val candidates = fileName.split("; ")
    for (candidate in candidates) {
        fileName = candidate.substringAfter("filename=")
                .removeSurrounding("\"")
        if (fileName.isNotBlank()) return fileName
    }

    return null
}

abstract class AbstractHttpResponse : HttpResponse {

    override fun contentType(): String? {
        return this.firstHeader("content-type")
    }

    override fun containsHeader(headerName: String): Boolean? {
        return headers()?.any { it.name().equalIgnoreCase(headerName) } ?: false
    }

    override fun headers(headerName: String): Array<String>? {
        return headers()
                ?.filter { it.name().equalIgnoreCase(headerName) }
                ?.map { it.value() }
                ?.filter { it != null }
                ?.map { it as String }
                ?.toTypedArray()
    }

    override fun firstHeader(headerName: String): String? {
        return headers()
                ?.filter { it.name().equalIgnoreCase(headerName) }
                ?.map { it.value() }
                ?.first { it != null }
    }

    override fun lastHeader(headerName: String): String? {
        return headers()
                ?.filter { it.name().equalIgnoreCase(headerName) }
                ?.map { it.value() }
                ?.last { it != null }
    }

    override fun string(): String? {
        var charset: Charset? = KitUtils.safe { ContentType.parse(this.contentType())?.charset }
        if (charset == null) {
            charset = Consts.UTF_8
        }
        return this.string(charset!!)
    }

    override fun string(charset: Charset): String? {
        return this.bytes()?.let { String(it, charset) }
    }

    override fun stream(): InputStream {
        return ByteArrayInputStream(bytes())
    }

    abstract override fun bytes(): ByteArray?
}

@ScriptTypeName("header")
interface HttpHeader {

    fun name(): String?

    fun value(): String?

}

@ScriptTypeName("header")
class BasicHttpHeader : HttpHeader {

    constructor()

    constructor(name: String?, value: String?) {
        this.name = name
        this.value = value
    }

    private var name: String? = null

    private var value: String? = null

    override fun name(): String? = name
    override fun value(): String? = value

    fun setName(name: String?) {
        this.name = name
    }

    fun setValue(value: String?) {
        this.value = value
    }
}

@ScriptTypeName("param")
interface HttpParam {

    fun name(): String?

    fun value(): String?

    /**
     * text/file
     */
    fun type(): String?
}

@ScriptTypeName("param")
class BasicHttpParam : HttpParam {

    constructor()

    constructor(name: String?, value: String?) {
        this.name = name
        this.value = value
    }

    constructor(name: String?, value: String?, type: String?) {
        this.name = name
        this.value = value
        this.type = type
    }

    private var name: String? = null

    private var value: String? = null

    /**
     * text/file
     */
    private var type: String? = null

    override fun name(): String? = name
    override fun value(): String? = value
    override fun type(): String? = type

    fun setName(name: String?) {
        this.name = name
    }

    fun setValue(value: String?) {
        this.value = value
    }

    fun setType(type: String?) {
        this.type = type
    }

}