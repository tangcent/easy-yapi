package com.itangcent.common.model

import com.itangcent.common.utils.SimpleExtensible
import java.io.Serializable

/**
 * Represents an HTTP header field.
 */
class Header : SimpleExtensible(), Serializable {
    var name: String? = null

    var value: String? = null

    var desc: String? = null

    var required: Boolean? = null
}