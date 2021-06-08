package com.itangcent.http

import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.common.kit.KitUtils
import com.itangcent.common.kit.equalIgnoreCase
import org.apache.http.Consts
import org.apache.http.entity.ContentType
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.InputStream
import java.nio.charset.Charset
import java.util.*

/**
 * This interface represents an abstract store for [Cookie] objects.
 */
@ScriptTypeName("cookieStore")
interface CookieStore {

    /**
     * Adds an [Cookie], replacing any existing equivalent cookies.
     * If the given cookie has already expired it will not be added, but existing
     * values will still be removed.
     *
     * @param cookie the [Cookie] to be added
     */
    fun addCookie(cookie: Cookie?)

    /**
     * Adds [Cookie]s to the store.
     * It will replacing any existing equivalent cookies.
     * The cookie which has already expired will not be added, but existing
     * values will still be removed.
     *
     * @param cookies the [Cookie]s to be added
     */
    fun addCookies(cookies: Array<Cookie>?)

    /**
     * Returns all cookies contained in this store.
     *
     * @return the list of all cookies which is unmodifiable
     */
    fun cookies(): List<Cookie>

    /**
     * Clears all cookies.
     */
    fun clear()

    /**
     * Create a cookie which is mutable.
     * But it will not be add to the store immediately.
     * You must explicitly call [addCookies] to add the cookie to the store
     *
     */
    fun newCookie(): MutableCookie
}

/**
 * Cookie interface represents a HTTP(s) Cookie.
 */
@ScriptTypeName("cookie")
interface Cookie {

    /**
     * Returns the name.
     *
     * @return String name The name
     */
    fun getName(): String?

    /**
     * Returns the value.
     *
     * @return String value The current value.
     */
    fun getValue(): String?

    /**
     * Returns the comment describing the purpose of this cookie, or
     * {@code null} if no such comment has been defined.
     * Compatible only.Obsolete.
     * @return comment
     */
    fun getComment(): String?

    /**
     * If a user agent (web browser) presents this cookie to a user, the
     * cookie's purpose will be described by the information at this URL.
     * Compatible only.Obsolete.
     */
    fun getCommentURL(): String?

    /**
     * Returns the expiration [Date] of the cookie, or {@code null}
     * if none exists.
     *
     * @return Expiration [Date], or {@code null}.
     */
    fun getExpiryDate(): Long?

    /**
     * Returns {@code false} if the cookie should be discarded at the end
     * of the "session"; {@code true} otherwise.
     *
     * @return {@code false} if the cookie should be discarded at the end
     *         of the "session"; {@code true} otherwise
     */
    fun isPersistent(): Boolean

    /**
     * Returns domain attribute of the cookie. The value of the Domain
     * attribute specifies the domain for which the cookie is valid.
     *
     * @return the value of the domain attribute.
     */
    fun getDomain(): String?

    /**
     * Returns the path attribute of the cookie. The value of the Path
     * attribute specifies the subset of URLs on the origin server to which
     * this cookie applies.
     *
     * @return The value of the path attribute.
     */
    fun getPath(): String?

    /**
     * Get the Port attribute. It restricts the ports to which a cookie
     * may be returned in a Cookie request header.
     */
    fun getPorts(): IntArray?

    /**
     * Indicates whether this cookie requires a secure connection.
     *
     * @return  {@code true} if this cookie should only be sent
     *          over secure connections, {@code false} otherwise.
     */
    fun isSecure(): Boolean

    /**
     * Returns the version of the cookie specification to which this
     * cookie conforms.
     * Compatible only.Obsolete.
     *
     * @return the version of the cookie.
     */
    fun getVersion(): Int?
}

@ScriptTypeName("cookie")
interface MutableCookie : Cookie {

    fun setName(name: String?)

    fun setValue(value: String?)

    fun setComment(comment: String?)

    fun setCommentURL(commentURL: String?)

    /**
     * Sets expiration date.
     *
     * @param expiryDate the [java.util.Date] after which this cookie is no longer valid.
     *
     * @see Cookie.getExpiryDate
     */
    fun setExpiryDate(expiryDate: Long?)

    /**
     * Sets the domain attribute.
     *
     * @param domain The value of the domain attribute
     *
     * @see Cookie.getDomain
     */
    fun setDomain(domain: String?)

    /**
     * Sets the path attribute.
     *
     * @param path The value of the path attribute
     *
     * @see Cookie.getPath
     *
     */
    fun setPath(path: String?)

    /**
     * Sets the Port attribute. It restricts the ports to which a cookie
     * may be returned in a Cookie request header.
     * Compatible only.Obsolete.
     */
    fun setPorts(ports: IntArray?)

    /**
     * Sets the secure attribute of the cookie.
     * <p>
     * When {@code true} the cookie should only be sent
     * using a secure protocol (https).  This should only be set when
     * the cookie's originating server used a secure protocol to set the
     * cookie's value.
     *
     * @param secure The value of the secure attribute
     *
     * @see isSecure
     */
    fun setSecure(secure: Boolean)

    /**
     * Sets the version of the cookie specification to which this
     * cookie conforms.
     * Compatible only.Obsolete.
     *
     * @param version the version of the cookie.
     *
     * @see Cookie.getVersion
     */
    fun setVersion(version: Int?)
}

/**
 *
 * A HttpRequest represents an HTTP request that can be called.
 */
@ScriptTypeName("request")
interface HttpRequest {

    /**
     * Get the HTTP method to request
     */
    fun method(): String

    /**
     * Set the HTTP method to request
     * @return current request
     */
    fun method(method: String): HttpRequest

    /**
     * Get the url to request
     */
    fun url(): String?

    /**
     * Set the url to request
     * @return current request
     */
    fun url(url: String): HttpRequest

    //region header---------------------------------------------------------

    /**
     * Return true if headers with the given name be found in this request.
     *
     * <p>Header name comparison is case insensitive.
     *
     * @param headerName the header name to find
     * @return {@code true} if at least one header with the name be found, {@code false} otherwise
     */
    fun containsHeader(headerName: String): Boolean

    /**
     * Gets the value of the first header with the given name.
     *
     * <p>Header name comparison is case insensitive.
     *
     * @param headerName the name of the header to get
     * @return value of the first header or {@code null}
     */
    fun firstHeader(headerName: String): String?

    /**
     * Gets the value of the last header with the given name.
     *
     * <p>Header name comparison is case insensitive.
     *
     * @param headerName the name of the header to get
     * @return value of the last header or {@code null}
     */
    fun lastHeader(headerName: String): String?

    /**
     * Gets all of the headers with the given name.  The returned array
     * maintains the relative order in which the headers were added.
     *
     * <p>Header name comparison is case insensitive.
     *
     * @param headerName the name of the header(s) to get
     *
     * @return an unmodifiable array of headers with the given name.
     */
    fun headers(headerName: String): Array<String>?

    /**
     * Gets all of the headers be add to this request.
     *
     * @return an array of headers which is unmodifiable
     */
    fun headers(): Array<HttpHeader>?

    /**
     * Adds the given header to the request.
     * The order in which this header was added is preserved.
     *
     * @param header the header to add
     * @return current request
     */
    fun header(header: HttpHeader): HttpRequest

    /**
     * Adds the given header to the request.
     * The order in which this header was added is preserved.
     * @param headerName the name of the header to add
     * @param headerValue the value of the header to add
     * @return current request
     */
    fun header(headerName: String, headerValue: String?): HttpRequest

    /**
     * Sets the header to the request overriding any
     * existing headers with same name.
     *
     * @param headerName the name of the header to set
     * @param headerValue the value of the header to set
     * @return current request
     */
    fun setHeader(headerName: String, headerValue: String?): HttpRequest

    /**
     * Removes the given header by special name.
     *
     * @param headerName the name of the header to remove
     * @return current request
     */
    fun removeHeaders(headerName: String): HttpRequest

    /**
     * Removes the given header by special name and value.
     *
     * @param headerName the name of the header to remove
     * @param headerValue the value of the header to remove
     * @return current request
     */
    fun removeHeader(headerName: String, headerValue: String?): HttpRequest

    //endregion header---------------------------------------------------------

    fun query(name: String, value: String?): HttpRequest

    fun querys(): List<HttpParam>?

    /**
     * Set the body to be sent with the request.
     */
    fun body(body: Any?): HttpRequest

    /**
     * Get the body to be sent with the request.
     */
    fun body(): Any?

    /**
     * Adds the given param to the request.
     * The order in which this param was added is preserved.
     *
     * @param paramName the name of the param to add
     * @param paramValue the value of the param to add
     * @return current request
     */
    fun param(paramName: String, paramValue: String?): HttpRequest

    /**
     * Adds the given param with file type to the request.
     * The param will be sent as a binary body part backed by the given file.
     * The order in which this param was added is preserved.
     *
     * @param paramName the name of the param to add
     * @param filePath the filePath as the value of the param to add
     * @return current request
     */
    fun fileParam(paramName: String, filePath: String?): HttpRequest

    /**
     * Gets all of the params be add to this request.
     *
     * @return an array of params which is unmodifiable
     */
    fun params(): Array<HttpParam>?

    /**
     * Return true if params with the given name be found in this request.
     *
     * <p>Param name comparison is case sensitive.
     *
     * @param paramName the param name to find
     * @return {@code true} if at least one param with the name be found, {@code false} otherwise
     */
    fun containsParam(paramName: String): Boolean

    /**
     * Gets all of the params with the given name.  The returned array
     * maintains the relative order in which the params were added.
     *
     * <p>param name comparison is case sensitive.
     *
     * @param paramName the name of the param(s) to get
     *
     * @return an unmodifiable array of params with the given name.
     */
    fun params(paramName: String): Array<HttpParam>?

    /**
     * Gets all value of the params with the given name.  The returned array
     * maintains the relative order in which the params were added.
     *
     * <p>param name comparison is case sensitive.
     *
     * @param paramName the name of the param(s) to get
     *
     * @return an unmodifiable array of value of params with the given name.
     */
    fun paramValues(paramName: String): Array<String>?

    /**
     * Gets the first param with the given name.
     *
     * <p>param name comparison is case sensitive.
     *
     * @param paramName the name of the param to get
     * @return the first param or {@code null}
     */
    fun firstParam(paramName: String): HttpParam?

    /**
     * Gets the value of the first param with the given name.
     *
     * <p>param name comparison is case sensitive.
     *
     * @param paramName the name of the param to get
     * @return value of the first param or {@code null}
     */
    fun firstParamValue(paramName: String): String?

    /**
     * Gets the last param with the given name.
     *
     * <p>param name comparison is case sensitive.
     *
     * @param paramName the name of the param to get
     * @return the last param or {@code null}
     */
    fun lastParam(paramName: String): HttpParam?

    /**
     * Gets the value of the last param with the given name.
     *
     * <p>param name comparison is case sensitive.
     *
     * @param paramName the name of the param to get
     * @return value of the last param or {@code null}
     */
    fun lastParamValue(paramName: String): String?

    /**
     * Obtains the Content-Type header value, if known.
     * This is the header that should be used when sending the entity.
     * It can include a charset attribute.
     *
     * @return  the Content-Type header value for this request, or
     *          {@code null} if the content type is unknown
     */
    fun contentType(): String?

    /**
     * Set the Content-Type header value.
     * Overriding existing headers with Content-Type.
     *
     * @return current request
     */
    fun contentType(contentType: String): HttpRequest

    /**
     * Executes HTTP request
     *
     * @return  the response to the request
     */
    fun call(): HttpResponse
}

abstract class AbstractHttpRequest : HttpRequest {

    /**
     * The HTTP method to request
     */
    private var method: String = "GET"

    /**
     * Get the HTTP method to request
     */
    override fun method(): String {
        return this.method
    }

    /**
     * Set the HTTP method to request
     * @return current request
     */
    override fun method(method: String): HttpRequest {
        this.method = method
        return this
    }

    /**
     * The url to request
     */
    private var url: String? = null

    /**
     * Get the url to request
     */
    override fun url(): String? {
        return this.url
    }

    /**
     * Set the url to request
     * @return current request
     */
    override fun url(url: String): HttpRequest {
        this.url = url
        return this
    }

    //region header---------------------------------------------------------

    /**
     * All of the headers be add to this request.
     */
    private var headers: List<HttpHeader>? = null

    /**
     * Return true if headers with the given name be found in this request.
     *
     * <p>Header name comparison is case insensitive.
     *
     * @param headerName the header name to find
     * @return {@code true} if at least one header with the name be found, {@code false} otherwise
     */
    override fun containsHeader(headerName: String): Boolean {
        return headers?.any { it.name().equalIgnoreCase(headerName) } ?: false
    }

    /**
     * Gets all of the headers with the given name.  The returned array
     * maintains the relative order in which the headers were added.
     *
     * <p>Header name comparison is case insensitive.
     *
     * @param headerName the name of the header(s) to get
     *
     * @return an unmodifiable array of headers with the given name.
     */
    override fun headers(headerName: String): Array<String>? {
        return headers
            ?.filter { it.name().equalIgnoreCase(headerName) }
            ?.mapNotNull { it.value() }
            ?.takeIf { it.isNotEmpty() }
            ?.toTypedArray()
    }

    /**
     * Gets the value of the first header with the given name.
     *
     * <p>Header name comparison is case insensitive.
     *
     * @param headerName the name of the header to get
     * @return value of the first header or {@code null}
     */
    override fun firstHeader(headerName: String): String? {
        return headers
            ?.filter { it.name().equalIgnoreCase(headerName) }
            ?.map { it.value() }
            ?.firstOrNull { it != null }
    }

    /**
     * Gets the value of the last header with the given name.
     *
     * <p>Header name comparison is case insensitive.
     *
     * @param headerName the name of the header to get
     * @return value of the last header or {@code null}
     */
    override fun lastHeader(headerName: String): String? {
        return headers
            ?.filter { it.name().equalIgnoreCase(headerName) }
            ?.map { it.value() }
            ?.lastOrNull { it != null }
    }

    /**
     * Gets all of the headers be add to this request.
     *
     * @return an array of headers which is unmodifiable
     */
    override fun headers(): Array<HttpHeader>? {
        return headers?.toTypedArray()
    }

    /**
     * Try init headers if it is null.
     *
     * @return headers cast as mutable list.
     */
    private fun tryInitHeaders(): MutableList<HttpHeader> {
        if (headers == null) {
            headers = LinkedList()
        }
        return (this.headers as MutableList<HttpHeader>?)!!
    }

    /**
     * Adds the given header to the request.
     * The order in which this header was added is preserved.
     *
     * @param header the header to add
     * @return current request
     */
    override fun header(header: HttpHeader): HttpRequest {
        tryInitHeaders().add(header)
        return this
    }

    /**
     * Adds the given header to the request.
     * The order in which this header was added is preserved.
     *
     * @param headerName the name of the header to add
     * @param headerValue the value of the header to add
     * @return current request
     */
    override fun header(headerName: String, headerValue: String?): HttpRequest {
        tryInitHeaders().add(BasicHttpHeader(headerName, headerValue))
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
        removeHeaders(headerName)
        header(headerName, headerValue)
        return this
    }

    /**
     * Removes the given header by special name.
     *
     * @param headerName the name of the header to remove
     * @return current request
     */
    override fun removeHeaders(headerName: String): HttpRequest {
        tryInitHeaders().removeAll { it.name().equalIgnoreCase(headerName) }
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
        tryInitHeaders().removeAll { it.name().equalIgnoreCase(headerName) && it.value() == headerValue }
        return this
    }

    /**
     * Obtains the Content-Type header value, if known.
     * This is the header that should be used when sending the entity.
     * It can include a charset attribute.
     *
     * @return  the Content-Type header value for this request, or
     *          {@code null} if the content type is unknown
     */
    override fun contentType(): String? {
        return this.firstHeader("content-type")
    }

    /**
     * Set the Content-Type header value.
     * Overriding existing headers with Content-Type.
     *
     * @return current request
     */
    override fun contentType(contentType: String): HttpRequest {
        return this.setHeader("Content-Type", contentType)
    }

    //endregion header---------------------------------------------------------

    /**
     * All of the query params be add to this request.
     */
    private var querys: List<HttpParam>? = null

    /**
     * Try init querys if it is null.
     *
     * @return querys cast as mutable list.
     */
    private fun tryInitQuerys(): MutableList<HttpParam> {
        if (querys == null) {
            querys = LinkedList()
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

    /**
     * All of the form params be add to this request.
     */
    private var form: List<HttpParam>? = null

    /**
     * Try init form params if it is null.
     *
     * @return form params cast as mutable list.
     */
    private fun tryInitFormParams(): MutableList<HttpParam> {
        if (form == null) {
            form = LinkedList()
        }
        return (this.form as MutableList<HttpParam>?)!!
    }

    /**
     * Adds the given param to the request.
     * The order in which this param was added is preserved.
     *
     * @param paramName the name of the param to add
     * @param paramValue the value of the param to add
     * @return current request
     */
    override fun param(paramName: String, paramValue: String?): HttpRequest {
        tryInitFormParams().add(BasicHttpParam(paramName, paramValue))
        return this
    }

    /**
     * Adds the given param with file type to the request.
     * The order in which this param was added is preserved.
     *
     * @param paramName the name of the param to add
     * @param filePath the filePath as the value of the param to add
     * @return current request
     */
    override fun fileParam(paramName: String, filePath: String?): HttpRequest {
        tryInitFormParams().add(BasicHttpParam(paramName, filePath, "file"))
        return this
    }

    /**
     * Return true if params with the given name be found in this request.
     *
     * <p>Param name comparison is case sensitive.
     *
     * @param paramName the param name to find
     * @return {@code true} if at least one param with the name be found, {@code false} otherwise
     */
    override fun containsParam(paramName: String): Boolean {
        return form?.any { it.name() == paramName } ?: false
    }

    /**
     * Gets all of the params with the given name.  The returned array
     * maintains the relative order in which the params were added.
     *
     * <p>param name comparison is case sensitive.
     *
     * @param paramName the name of the param(s) to get
     *
     * @return an unmodifiable array of params with the given name.
     */
    override fun params(paramName: String): Array<HttpParam>? {
        return form
            ?.filter { it.name() == paramName }
            ?.toTypedArray()
    }

    /**
     * Gets all value of the params with the given name.  The returned array
     * maintains the relative order in which the params were added.
     *
     * <p>param name comparison is case sensitive.
     *
     * @param paramName the name of the param(s) to get
     *
     * @return an unmodifiable array of value of params with the given name.
     */
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

    /**
     * Gets all of the params be add to this request.
     *
     * @return an array of params which is unmodifiable
     */
    override fun params(): Array<HttpParam>? {
        return form?.toTypedArray()
    }

    //endregion---------------------------------------------------------

    /**
     * The body that can be sent with an HTTP request.
     */
    private var body: Any? = null

    /**
     * Set the body to be sent with the request.
     */
    override fun body(body: Any?): HttpRequest {
        this.body = body
        return this
    }

    /**
     * Get the body to be sent with the request.
     */
    override fun body(): Any? {
        return this.body
    }

}

@ScriptTypeName("response")
interface HttpResponse : Closeable {

    /**
     * Obtains the status of this response.
     *
     * @return  the status of the response, or {@code null} if not yet set
     */
    fun code(): Int?

    /**
     * Returns all the headers of this [HttpResponse].
     * Headers are orderd in the sequence they will be sent over a connection.
     *
     * @return all the headers of this [HttpResponse]
     */
    fun headers(): List<HttpHeader>?

    /**
     * Obtains the string message of this response with the character in [contentType].
     *
     * @return  the response string, or
     *          {@code null} if there is none
     */
    fun string(): String?

    /**
     * Obtains the string message of this response with the specified character set.
     *
     * @return  the response string, or
     *          {@code null} if there is none
     */
    fun string(charset: Charset): String?

    /**
     * Creates a [InputStream] of the message of this response.
     */
    fun stream(): InputStream

    /**
     * Obtains the Content-Type header value, if known.
     * This is the header that was received with the response.
     * It can include a charset attribute.
     *
     * @return  the Content-Type header for this response, or
     *          {@code null} if the content type is unknown
     */
    fun contentType(): String?

    /**
     * Obtains the bytes message of this response.
     *
     * @return  the response bytes, or
     *          {@code null} if there is none
     */
    fun bytes(): ByteArray?

    /**
     * Return true if headers with the given name be found in this response.
     *
     * <p>Header name comparison is case insensitive.
     *
     * @param headerName the header name to find
     * @return {@code true} if at least one header with the name be found, {@code false} otherwise
     */
    fun containsHeader(headerName: String): Boolean?

    /**
     * Gets all of the headers with the given name.  The returned array
     * maintains the relative order in which the headers were sent.
     *
     * <p>Header name comparison is case insensitive.
     *
     * @param headerName the name of the header(s) to get
     *
     * @return an unmodifiable array of headers with the given name.
     */
    fun headers(headerName: String): Array<String>?

    /**
     * Gets the value of the first header with the given name.
     *
     * <p>Header name comparison is case insensitive.
     *
     * @param headerName the name of the header to get
     * @return value of the first header or {@code null}
     */
    fun firstHeader(headerName: String): String?

    /**
     * Gets the value of the last header with the given name.
     *
     * <p>Header name comparison is case insensitive.
     *
     * @param headerName the name of the header to get
     * @return value of the last header or {@code null}
     */
    fun lastHeader(headerName: String): String?

    /**
     * The request which be called.
     */
    fun request(): HttpRequest
}

/**
 * Try find filename in header `Content-Disposition`.
 */
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

    /**
     * Obtains the Content-Type header value, if known.
     * This is the header that was received with the response.
     * It can include a charset attribute.
     *
     * @return  the Content-Type header for this response, or
     *          {@code null} if the content type is unknown
     */
    override fun contentType(): String? {
        return this.firstHeader("content-type")
    }

    /**
     * Return true if headers with the given name be found in this response.
     *
     * <p>Header name comparison is case insensitive.
     *
     * @param headerName the header name to find
     * @return {@code true} if at least one header with the name be found, {@code false} otherwise
     */
    override fun containsHeader(headerName: String): Boolean? {
        return headers()?.any { it.name().equalIgnoreCase(headerName) } ?: false
    }

    /**
     * Gets all of the headers with the given name.  The returned array
     * maintains the relative order in which the headers were sent.
     *
     * <p>Header name comparison is case insensitive.
     *
     * @param headerName the name of the header(s) to get
     *
     * @return an unmodifiable array of headers with the given name.
     */
    override fun headers(headerName: String): Array<String>? {
        return headers()
            ?.filter { it.name().equalIgnoreCase(headerName) }
            ?.mapNotNull { it.value() }
            ?.toTypedArray()
    }

    /**
     * Gets the value of the first header with the given name.
     *
     * <p>Header name comparison is case insensitive.
     *
     * @param headerName the name of the header to get
     * @return value of the first header or {@code null}
     */
    override fun firstHeader(headerName: String): String? {
        return headers()
            ?.filter { it.name().equalIgnoreCase(headerName) }
            ?.map { it.value() }
            ?.firstOrNull { it != null }
    }

    /**
     * Gets the value of the last header with the given name.
     *
     * <p>Header name comparison is case insensitive.
     *
     * @param headerName the name of the header to get
     * @return value of the last header or {@code null}
     */
    override fun lastHeader(headerName: String): String? {
        return headers()
            ?.filter { it.name().equalIgnoreCase(headerName) }
            ?.map { it.value() }
            ?.lastOrNull { it != null }
    }

    /**
     * Obtains the string message of this response with the character in [contentType].
     *
     * @return  the response string, or
     *          {@code null} if there is none
     */
    override fun string(): String? {
        val charset: Charset? = KitUtils.safe { ContentType.parse(this.contentType())?.charset }
            ?: Consts.UTF_8
        return this.string(charset!!)
    }

    /**
     * Obtains the string message of this response with the specified character set.
     *
     * @return  the response string, or
     *          {@code null} if there is none
     */
    override fun string(charset: Charset): String? {
        return this.bytes()?.let { String(it, charset) }
    }

    /**
     * Creates a [InputStream] with [bytes]
     */
    override fun stream(): InputStream {
        return ByteArrayInputStream(bytes())
    }

    /**
     * Obtains the bytes message of this response.
     *
     * @return  the response bytes, or
     *          {@code null} if there is none
     */
    abstract override fun bytes(): ByteArray?
}

/**
 * Represents an HTTP header field.
 */
@ScriptTypeName("header")
interface HttpHeader {

    /**
     * Gets the name of this header.
     *
     * @return the name of this header
     */
    fun name(): String?

    /**
     * Gets the value of this header.
     *
     * @return the value of this header
     */
    fun value(): String?

}

@ScriptTypeName("header")
class BasicHttpHeader : HttpHeader {

    constructor()

    constructor(name: String?, value: String?) {
        this.name = name
        this.value = value
    }

    /**
     * The name of this header.
     */
    private var name: String? = null

    /**
     * The value of this header.
     */
    private var value: String? = null

    /**
     * Gets the name of this header.
     *
     * @return the name of this header
     */
    override fun name(): String? = name

    /**
     * Gets the value of this header.
     *
     * @return the value of this header
     */
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

    /**
     * Gets the value of this param.
     *
     * @return the value of this param
     */
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
        this.type = "text"
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