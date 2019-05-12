package com.itangcent.common.model

import java.io.Serializable

class Response: Serializable {

    var headers: ArrayList<Header>? = null

    /**
     * raw/json/xml
     */
    var bodyType: String? = null

    var body: Any? = null

    var code: Int? = null
}