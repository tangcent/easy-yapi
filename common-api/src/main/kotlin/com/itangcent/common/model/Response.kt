package com.itangcent.common.model

import com.itangcent.common.utils.SimpleExtensible
import java.io.Serializable

/**
 * A response of [Request].
 */
open class Response : SimpleExtensible(), Serializable {

    var headers: MutableList<Header>? = null

    /**
     * raw/json/xml
     */
    var bodyType: String? = null

    var body: Any? = null

    var bodyDesc: String? = null

    var code: Int? = null
}