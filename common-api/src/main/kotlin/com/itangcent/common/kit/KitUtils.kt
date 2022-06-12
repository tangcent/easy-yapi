package com.itangcent.common.kit

import com.itangcent.common.spi.SpiUtils
import com.itangcent.utils.DefaultJsonSupport
import com.itangcent.utils.JsonSupport

object KitUtils {

    fun <T> fromBool(boolean: Boolean, whenTrue: T, whenFalse: T): T {
        return when (boolean) {
            true -> whenTrue
            false -> whenFalse
        }
    }
}

fun <T> Boolean?.or(whenTrue: T, whenFalse: T): T {
    return when (this) {
        true -> whenTrue
        else -> whenFalse
    }
}

fun Any?.toJson(): String? {
    if (this == null) {
        return null
    }

    if (this is String) {
        return this
    }

    return (SpiUtils.loadService(JsonSupport::class) ?: DefaultJsonSupport).toJson(this)
}

fun String.headLine(): String? {
    if (this.isBlank()) return null

    var index = -1
    val trimStr = this.trim()
    for ((i, c) in trimStr.withIndex()) {
        if (c == '\r' || c == '\n') {
            index = i
            break
        }
    }
    if (index == -1) {
        return this
    }
    return trimStr.substring(0, index)
}

fun String?.equalIgnoreCase(str: String?): Boolean {
    return this.equals(str, ignoreCase = true)
}
