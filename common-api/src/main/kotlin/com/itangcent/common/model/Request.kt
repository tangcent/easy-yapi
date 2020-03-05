package com.itangcent.common.model

import com.itangcent.common.constant.HttpMethod

open class Request : Doc() {

    var path: String? = null

    var method: String? = null

    var headers: MutableList<Header>? = null

    var paths: MutableList<PathParam>? = null

    var querys: MutableList<Param>? = null

    var formParams: MutableList<FormParam>? = null

    /**
     * raw/json/xml
     */
    var bodyType: String? = null

    var body: Any? = null

    var response: MutableList<Response>? = null
}

fun Request.getContentType(): String? {
    return this.header("content-type")
}

fun Request.header(name: String): String? {
    if (this.headers.isNullOrEmpty()) {
        return null
    }
    val lowerName = name.toLowerCase()
    return this.headers!!.filter { it.name?.toLowerCase() == lowerName }
            .map { it.value }
            .firstOrNull()
}

fun Request.hasBody(): Boolean {
    return this.method != null && this.method != HttpMethod.GET
}

fun Request.hasMethod(): Boolean {
    return this.method != null && this.method != HttpMethod.NO_METHOD
}