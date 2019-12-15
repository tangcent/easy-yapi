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

fun String?.concat(any: Any?, separator: CharSequence = "\n"): String? {
    if (this == null) {
        return any?.toString()
    } else if (any == null) {
        return this
    } else {
        return this + separator + any
    }
}

fun String?.notEmpty(): Boolean {
    return !this.isNullOrEmpty()
}

fun String.headLine(): String? {
    if (this.isBlank()) return null

    val lines = this.lines()
    return if (lines.size > 1) {//multi line
        lines.firstOrNull { it.isNotBlank() }
    } else {
        this
    }
}