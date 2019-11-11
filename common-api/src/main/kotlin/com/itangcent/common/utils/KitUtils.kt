package com.itangcent.common.utils

object KitUtils {

    fun <T> fromBool(boolean: Boolean, whenTrue: T, whenFalse: T): T {
        return when (boolean) {
            true -> whenTrue
            false -> whenFalse
        }
    }
}
