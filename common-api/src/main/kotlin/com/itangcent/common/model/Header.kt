package com.itangcent.common.model

import java.io.Serializable

/**
 * Represents an HTTP header field.
 */
class Header : Extensible(), Serializable {
    var name: String? = null

    var value: String? = null

    var desc: String? = null

    var required: Boolean? = null
}