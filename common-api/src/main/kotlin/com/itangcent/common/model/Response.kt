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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Response

        if (headers != other.headers) return false
        if (bodyType != other.bodyType) return false
        if (body != other.body) return false
        if (bodyDesc != other.bodyDesc) return false
        if (code != other.code) return false

        return true
    }

    override fun hashCode(): Int {
        var result = headers?.hashCode() ?: 0
        result = 31 * result + (bodyType?.hashCode() ?: 0)
        result = 31 * result + (body?.hashCode() ?: 0)
        result = 31 * result + (bodyDesc?.hashCode() ?: 0)
        result = 31 * result + (code ?: 0)
        return result
    }

}