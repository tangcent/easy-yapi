package com.itangcent.common.model

import java.io.Serializable

open class Response : Extensible(), Serializable {

    var headers: MutableList<Header>? = null

    /**
     * raw/json/xml
     */
    var bodyType: String? = null

    var body: Any? = null

    var bodyDesc: String? = null

    var code: Int? = null
}