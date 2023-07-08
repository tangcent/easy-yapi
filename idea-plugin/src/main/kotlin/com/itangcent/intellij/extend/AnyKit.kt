package com.itangcent.intellij.extend

import com.itangcent.common.utils.asHashMap
import com.itangcent.common.utils.isOriginal

fun Boolean.toInt(): Int {
    return if (this) 1 else 0
}

fun Any?.toPrettyString(): String? {
    if (this == null) return null
    if (this is String) return this
    if (this is Array<*>) return "[" + this.joinToString(separator = ", ") { it.toPrettyString() ?: "null" } + "]"
    if (this is Collection<*>) return "[" + this.joinToString(separator = ", ") { it.toPrettyString() ?: "null" } + "]"
    if (this is Map<*, *>) return "{" + this.entries.joinToString(separator = ", ") {
        (it.key.toPrettyString() ?: "null") + ": " + (it.value.toPrettyString() ?: "null")
    } + "}"
    return this.toString()
}

@Suppress("UNCHECKED_CAST")
fun <K, V> Any.asHashMap(): HashMap<K, V> {
    if (this is HashMap<*, *>) {
        return this as HashMap<K, V>
    }

    if (this is Map<*, *>) {
        return this.asHashMap() as HashMap<K, V>
    }
    return HashMap()
}

/**
 * Return the object if it is not an "original" value, or null otherwise.
 *
 * An "original" value is defined as follows:
 *
 * - The default value of a primitive type (e.g., 0 for Int)
 * - The default value of a nullable primitive type (e.g., null for Int?)
 * - The empty string ("")
 * - The string "0"
 * - An array or collection containing only original values
 */
fun Any?.takeIfNotOriginal(): Any? {
    return if (this.isOriginal()) {
        null
    } else {
        this
    }
}

/**
 * Check if the object is "special" (not an original value), as defined by the takeIfNotOriginal function.
 *
 * An "original" value is defined as follows:
 *
 * - The default value of a primitive type (e.g., 0 for Int)
 * - The default value of a nullable primitive type (e.g., null for Int?)
 * - The empty string ("")
 * - The string "0"
 * - An array or collection containing only original values
 */
fun Any?.isSpecial(): Boolean {
    return when (val obj = this) {
        null -> {
            false
        }

        is String -> {
            obj.isNotBlank() && obj != "0" && obj != "0.0"
        }

        else -> !this.isOriginal()
    }
}

/**
 * Return the string if it is "special" (not an original value), or null otherwise.
 *
 * An "original" value is defined as follows:
 *
 * - The default value of a primitive type (e.g., 0 for Int)
 * - The default value of a nullable primitive type (e.g., null for Int?)
 * - The empty string ("")
 * - The string "0"
 * - An array or collection containing only original values
 */
fun String?.takeIfSpecial(): String? {
    return if (this.isSpecial()) {
        this
    } else {
        null
    }
}

/**
 * Return the first element of an array or a collection,
 * or the object itself if it is not an array or a collection.
 */
fun Any?.unbox(): Any? {
    if (this is Array<*>) {
        return this.firstOrNull().unbox()
    } else if (this is Collection<*>) {
        return this.firstOrNull().unbox()
    }
    return this
}
