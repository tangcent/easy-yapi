package com.itangcent.idea.plugin.api.export

import com.itangcent.common.utils.SimpleExtensible
import java.io.Serializable

/**
 * Additional field from rule.
 */
class AdditionalField : SimpleExtensible(), Serializable {
    var name: String? = null

    var defaultValue: String? = null

    var type: String? = null

    var desc: String? = null

    var required: Boolean? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AdditionalField

        if (name != other.name) return false
        if (defaultValue != other.defaultValue) return false
        if (type != other.type) return false
        if (desc != other.desc) return false
        if (required != other.required) return false
        if (map() != other.map()) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name?.hashCode() ?: 0
        result = 31 * result + (defaultValue?.hashCode() ?: 0)
        result = 31 * result + (type?.hashCode() ?: 0)
        result = 31 * result + (desc?.hashCode() ?: 0)
        result = 31 * result + (required?.hashCode() ?: 0)
        result = 31 * result + (map()?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "AdditionalField(name=$name, defaultValue=$defaultValue, type=$type, desc=$desc, required=$required)"
    }

}