package com.itangcent.utils

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
    if (sub != null && sub is MutableMap<*, *>) {
        return this as MutableMap<String, Any?>
    }
    if (this is MutableMap<*, *>) {
        if (sub == null) {
            val mutableSub = LinkedHashMap<String, Any?>()
            (this as MutableMap<String, Any?>)[key] = mutableSub
            return mutableSub
        }
        if (sub is Map<*, *>) {
            val mutableSub = sub.mutable()
            (this as MutableMap<String, Any?>)[key] = mutableSub
            return mutableSub as MutableMap<String, Any?>?
        }
    }
    return null
}
