package com.itangcent.common.kit

import com.itangcent.common.spi.SpiUtils
import com.itangcent.utils.DefaultJsonSupport
import com.itangcent.utils.JsonSupport
import kotlin.reflect.KClass

object KitUtils {

    fun <T> fromBool(boolean: Boolean, whenTrue: T, whenFalse: T): T {
        return when (boolean) {
            true -> whenTrue
            false -> whenFalse
        }
    }

    fun <T> safe(action: () -> T): T? {
        return try {
            action()
        } catch (e: Exception) {
            null
        }
    }

    fun <T> safe(ignoreThrowable: KClass<*>, action: () -> T): T? {
        return try {
            action()
        } catch (e: Exception) {
            if (ignoreThrowable.isInstance(e)) {
                null
            } else {
                throw e
            }
        }
    }

    fun <T> safe(vararg ignoreThrowable: KClass<*>, action: () -> T): T? {
        return try {
            action()
        } catch (e: Exception) {
            for (throwable in ignoreThrowable) {
                if (throwable.isInstance(e)) {
                    return null
                }
            }
            throw e
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
