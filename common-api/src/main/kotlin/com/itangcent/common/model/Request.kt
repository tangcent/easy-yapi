package com.itangcent.common.model

import com.itangcent.common.constant.HttpMethod
import com.itangcent.common.utils.firstOrNull

/**
 * Request represent A Http API.
 */
open class Request : Doc() {

    var path: URL? = null

    /**
     * The HTTP method.
     *
     * @see HttpMethod
     */
    var method: String? = null

    /**
     * All of the headers.
     */
    var headers: MutableList<Header>? = null

    var paths: MutableList<PathParam>? = null

    var querys: MutableList<Param>? = null

    var formParams: MutableList<FormParam>? = null

    /**
     * raw/json/xml
     */
    var bodyType: String? = null

    var body: Any? = null

    /**
     * The description of [body] if it is present.
     */
    var bodyAttr: String? = null

    var response: MutableList<Response>? = null
}

fun Request.getContentType(): String? {
    return this.header("content-type")
}

fun Request.hasForm(): Boolean {
    if (this.method == "GET") {
        return false
    }
    val contentType = this.getContentType() ?: return false
    return (contentType.contains("application/x-www-form-urlencoded")
            || contentType.contains("multipart/form-data"))
}

fun Request.header(name: String): String? {
    if (this.headers.isNullOrEmpty()) {
        return null
    }
    val lowerName = name.toLowerCase()
    return this.headers!!
            .stream()
            .filter { it.name?.toLowerCase() == lowerName }
            .map { it.value }
            .firstOrNull()
}

fun Request.hasBody(): Boolean {
    return this.method != null && this.method != HttpMethod.GET
}

fun Request.hasMethod(): Boolean {
    return this.method != null && this.method != HttpMethod.NO_METHOD
}