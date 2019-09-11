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