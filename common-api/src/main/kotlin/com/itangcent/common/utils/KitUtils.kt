package com.itangcent.common.utils

object KitUtils {

    fun <T> fromBool(boolean: Boolean, whenTrue: T, whenFalse: T): T {
        return when (boolean) {
            true -> whenTrue
            false -> whenFalse
        }
    }
}

public fun StringBuilder.appendlnIfNotEmpty(): StringBuilder {
    if (this.isNotEmpty()) {
        this.appendln()
    }
    return this
}
