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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Header

        if (name != other.name) return false
        if (value != other.value) return false
        if (desc != other.desc) return false
        if (required != other.required) return false
        if (map() != other.map()) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name?.hashCode() ?: 0
        result = 31 * result + (value?.hashCode() ?: 0)
        result = 31 * result + (desc?.hashCode() ?: 0)
        result = 31 * result + (required?.hashCode() ?: 0)
        result = 31 * result + (map()?.hashCode() ?: 0)
        return result
    }

}