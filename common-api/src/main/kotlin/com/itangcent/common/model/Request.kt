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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Request

        if (path != other.path) return false
        if (method != other.method) return false
        if (headers != other.headers) return false
        if (paths != other.paths) return false
        if (querys != other.querys) return false
        if (formParams != other.formParams) return false
        if (bodyType != other.bodyType) return false
        if (body != other.body) return false
        if (bodyAttr != other.bodyAttr) return false
        if (response != other.response) return false

        return true
    }

    override fun hashCode(): Int {
        var result = path?.hashCode() ?: 0
        result = 31 * result + (method?.hashCode() ?: 0)
        result = 31 * result + (headers?.hashCode() ?: 0)
        result = 31 * result + (paths?.hashCode() ?: 0)
        result = 31 * result + (querys?.hashCode() ?: 0)
        result = 31 * result + (formParams?.hashCode() ?: 0)
        result = 31 * result + (bodyType?.hashCode() ?: 0)
        result = 31 * result + (body?.hashCode() ?: 0)
        result = 31 * result + (bodyAttr?.hashCode() ?: 0)
        result = 31 * result + (response?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "Request(path=$path, method=$method, headers=$headers, paths=$paths, querys=$querys, formParams=$formParams, bodyType=$bodyType, body=$body, bodyAttr=$bodyAttr, response=$response)"
    }
}

fun Request.getContentType(): String? {
    return this.header("content-type")
}

fun Request.hasForm(): Boolean {
    if (this.method == HttpMethod.GET || this.method != HttpMethod.NO_METHOD) {
        return false
    }

    val contentType = this.getContentType() ?: return false
    return !contentType.contains("application/json")
}

fun Request.header(name: String): String? {
    if (this.headers.isNullOrEmpty()) {
        return null
    }
    val lowerName = name.lowercase()
    return this.headers!!
        .asSequence()
        .filter { it.name?.lowercase() == lowerName }
        .map { it.value }
        .firstOrNull()
}

fun Request.hasBodyOrForm(): Boolean {
    return this.method != null && this.method != HttpMethod.GET
}

fun Request.hasMethod(): Boolean {
    return this.method != null && this.method != HttpMethod.NO_METHOD
}