package com.itangcent.utils

import com.itangcent.common.utils.isMutableMap
import com.itangcent.common.utils.mutable

fun Any?.isCollections(): Boolean {
    when (this) {
        null -> {
            return false
        }

        is Array<*> -> {
            return true
        }

        is Collection<*> -> {
            return true
        }

        is Map<*, *> -> {
            return true
        }

        else -> {
            return false
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun Map<*, *>.subMutable(key: String): MutableMap<String, Any?>? {
    val sub = this[key]
    if (sub != null && sub.isMutableMap()) {
        return sub as MutableMap<String, Any?>
    }
    if (this.isMutableMap()) {
        if (sub == null) {
            val mutableSub = LinkedHashMap<String, Any?>()
            try {
                (this as MutableMap<String, Any?>)[key] = mutableSub
            } catch (e: UnsupportedOperationException) {
                return null
            }
            return mutableSub
        }
        if (sub is Map<*, *>) {
            val mutableSub = sub.mutable()
            try {
                (this as MutableMap<String, Any?>)[key] = mutableSub
            } catch (e: UnsupportedOperationException) {
                return null
            }
            return mutableSub as MutableMap<String, Any?>?
        }
    }
    return null
}