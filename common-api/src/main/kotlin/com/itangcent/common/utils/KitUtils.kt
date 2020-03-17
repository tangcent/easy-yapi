package com.itangcent.common.utils

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
}

fun Any?.toJson(): String? {
    if (this == null) {
        return "null"
    }

    if (this is String) {
        return this
    }

    return GsonUtils.toJson(this)
}

fun String?.concat(any: Any?, separator: CharSequence = "\n"): String? {
    return when {
        this == null -> any?.toString()
        any == null -> this
        else -> this + separator + any
    }
}

fun String?.notNullOrEmpty(): Boolean {
    return !this.isNullOrEmpty()
}

fun String.headLine(): String? {
    if (this.isBlank()) return null

    var index = -1
    for ((i, c) in this.withIndex()) {
        if (c == '\r' || c == '\n') {
            index = i
            break
        }
    }
    if (index == -1) {
        return this
    }
    return substring(0, index)
}

fun String?.equalIgnoreCase(str: String?): Boolean {
    if (this == null) {
        return str == null
    } else if (str == null) {
        return false
    }
    return this.toLowerCase() == str.toLowerCase()
}