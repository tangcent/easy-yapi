package com.itangcent.common.model

class Response {

    var headers: ArrayList<Header>? = null

    /**
     * raw/json/xml
     */
    var bodyType: String? = null

    var body: Any? = null

    var code: Int? = null
}