package com.itangcent.common.model

class Response {

    var headers: Array<Header>? = null

    /**
     * raw/json/xml
     */
    var bodyType: String? = null

    var body: Any? = null
}