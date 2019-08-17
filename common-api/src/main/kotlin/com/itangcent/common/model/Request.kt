package com.itangcent.common.model

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