package com.itangcent.intellij.util

import com.itangcent.common.utils.KV
import java.util.function.BiConsumer

fun <V> KV<String, V>.forEachValid(action: (Map.Entry<String, V>) -> Unit) {
    this.forEach { entry ->
        if (!entry.key.isBlank() && !entry.key.startsWith("@")) {
            action(entry)
        }
    }
}

fun <V> KV<String, V>.forEachValid(action: BiConsumer<String, V>) {
    this.forEach { k, v ->
        if (!k.isBlank() && !k.startsWith("@")) {
            action.accept(k, v)
        }
    }
}

fun <V> KV<String, V>.forEachValid(action: (String, V) -> Unit) {
    this.forEach { k, v ->
        if (!k.isBlank() && !k.startsWith("@")) {
            action(k, v)
        }
    }
}

fun <K, V> Map<K, V>.forEachValid(action: (K, V) -> Unit) {
    this.forEach { k, v ->
        if (k == null) {
            return@forEach
        } else if (k is String) {
            if (k.isBlank() || k.startsWith("@")) {
                return@forEach
            }
        }
        action(k, v)
    }
}

@Suppress("UNCHECKED_CAST")
fun KV<String, Any?>.sub(key: String): KV<String, Any?> {
    var subKV: KV<String, Any?>? = this[key] as KV<String, Any?>?
    if (subKV == null) {
        subKV = KV.create()
        this[key] = subKV
    }
    return subKV
}

@Suppress("UNCHECKED_CAST")
public fun <K, V> Map<out K, V>.mutable(copy: Boolean = false): MutableMap<K, V> {
    return when {
        copy -> LinkedHashMap(this)
        this is MutableMap -> this as MutableMap<K, V>
        else -> LinkedHashMap(this)
    }
}