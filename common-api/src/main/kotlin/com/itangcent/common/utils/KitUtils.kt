package com.itangcent.common.utils

object KitUtils {

    fun <T> fromBool(boolean: Boolean, whenTrue: T, whenFalse: T): T {
        return when (boolean) {
            true -> whenTrue
            false -> whenFalse
        }
    }
}

fun StringBuilder.appendlnIfNotEmpty(): StringBuilder {
    if (this.isNotEmpty()) {
        this.appendln()
    }
    return this
}

fun Any?.tinyString(): String? {
    return when {
        this == null -> null
        this is String -> this
        this is Array<*> -> this.first().tinyString()
        this is Collection<*> -> this.first().tinyString()
        else -> this.toString()
    }
}

fun <K, V> Map<K, V>?.any(vararg ks: K): V? {
    if (this == null) return null
    for (k in ks) {
        val v = this[k]
        if (v != null) {
            return v
        }
    }
    return null
}

fun String.truncate(limit: Int, truncated: String = "..."): String {
    return when {
        this.length > limit -> this.substring(0, limit) + truncated
        else -> this
    }
}

fun String?.append(str: String?, split: String = " "): String? {
    return when {
        this == null -> str
        str == null -> this
        else -> this + split + str
    }
}

fun <K> MutableMap<K, String?>?.append(key: K, str: String?, split: String = " ") {
    if (this == null) return
    this[key] = this[key].append(str, split)
}