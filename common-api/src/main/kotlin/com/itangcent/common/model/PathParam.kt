package com.itangcent.common.model

import com.itangcent.common.utils.SimpleExtensible
import java.io.Serializable

class PathParam : SimpleExtensible(), NamedValue<String>, Serializable {
    override var name: String? = null

    override var value: String? = null

    var desc: String? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PathParam

        if (name != other.name) return false
        if (value != other.value) return false
        if (desc != other.desc) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name?.hashCode() ?: 0
        result = 31 * result + (value?.hashCode() ?: 0)
        result = 31 * result + (desc?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "PathParam(name=$name, value=$value, desc=$desc)"
    }
}
