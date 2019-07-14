package com.itangcent.common.model

import java.io.Serializable

open class Request : Serializable {

    var resource: Any? = null

    var name: String? = null

    var path: String? = null

    var method: String? = null

    var desc: String? = null

    var headers: ArrayList<Header>? = null

    var paths: ArrayList<PathParam>? = null

    var querys: ArrayList<Param>? = null

    var formParams: ArrayList<FormParam>? = null

    /**
     * raw/json/xml
     */
    var bodyType: String? = null

    var body: Any? = null

    var response: ArrayList<Response>? = null
}

fun Request.getContentType(): String? {
    if (this.headers.isNullOrEmpty()) {
        return null
    }
    return this.headers!!.filter { it.name?.toLowerCase() == "content-type" }
            .map { it.value }
            .firstOrNull()
}

fun Request.hasBody(): Boolean {
    return this.method != null && this.method != "GET"
}

typealias RequestHandle = (Request) -> Unit