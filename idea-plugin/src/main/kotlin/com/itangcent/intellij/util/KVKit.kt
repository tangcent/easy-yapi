package com.itangcent.intellij.util

import com.itangcent.common.utils.GsonUtils
import com.itangcent.common.utils.KV
import com.itangcent.intellij.extend.toPrettyString
import java.util.function.BiConsumer

fun <V> KV<String, V>.forEachValid(action: BiConsumer<String, V>) {
    this.forEachValid { k, v ->
        action.accept(k, v)
    }
}

fun <V> KV<String, V>.forEachValid(action: (String, V) -> Unit) {
    this.forEach { k, v ->
        if (k.startsWith("@")) {
            return@forEach
        }
        if (k.isBlank()) {
            if (this.size == 1) {
                action("key", v)
            }
            return@forEach
        }
        action(k, v)
    }
}

@Suppress("UNCHECKED_CAST")
fun <K, V> Map<K, V>.forEachValid(action: (K, V) -> Unit) {
    this.forEach { (k, v) ->
        if (k == null) {
            return@forEach
        } else if (k is String) {
            if (k.startsWith("@")) {
                return@forEach
            }
            if (k.isBlank()) {
                if (this.size == 1) {
                    action("key" as K, v)
                }
                return@forEach
            }
        }
        action(k, v)
    }
}

fun <K, V> Map<K, V>.validSize(): Int {
    return this.keys.count { it !is String || !it.startsWith('@') }
}

fun Map<*, *>.flatValid(consumer: FieldConsumer) {
    this.forEachValid { key, value ->
        if (key == null) return@forEachValid
        if (key is String) {
            flatValid(this, key, key, value, consumer)
        } else {
            GsonUtils.toJson(key).let { flatValid(this, it, it, value, consumer) }
        }
    }
}

private fun flatValid(parent: Map<*, *>?, path: String, key: String, value: Any?, consumer: FieldConsumer) {
    when (value) {
        null -> return
        is Collection<*> -> value.forEachIndexed { index, it ->
            if (it != null) {
                flatValid(parent, "$path[$index]", key, it, consumer)
            }
        }
        is Array<*> -> value.forEachIndexed { index, it ->
            if (it != null) {
                flatValid(parent, "$path[$index]", key, it, consumer)
            }
        }
        is Map<*, *> -> {
            value.forEachValid { k, v ->
                k.toPrettyString()?.let { flatValid(value, "$path.$it", it, v, consumer) }
            }
        }
        is String -> consumer.consume(parent, path, key, value)
        else -> consumer.consume(parent, path, key, GsonUtils.toJson(value))
    }
}

interface FieldConsumer {
    fun consume(parent: Map<*, *>?, path: String, key: String, value: Any?)
}

fun Any?.isComplex(root: Boolean = true): Boolean {
    when {
        this == null -> return false
        this is Collection<*> -> return this.any { it.isComplex(false) }
        this is Array<*> -> return this.any { it.isComplex(false) }
        this is Map<*, *> -> {
            if (!root) return true
            for (entry in this.entries) {
                val key = entry.key
                if (key != null && key is String && key.startsWith("@")) {
                    continue
                }
                if (entry.value.isComplex(false)) {
                    return true
                }
            }
            return false
        }
        this == Magics.FILE_STR -> return false
        else -> return false
    }
}

fun Any?.hasFile(): Boolean {
    when {
        this == null -> return false
        this is Collection<*> -> return this.any { it.hasFile() }
        this is Array<*> -> return this.any { it.hasFile() }
        this is Map<*, *> -> {
            for (entry in this.entries) {
                val key = entry.key
                if (key != null && key is String && key.startsWith("@")) {
                    continue
                }
                if (entry.value.hasFile()) {
                    return true
                }
            }
            return false
        }
        this == Magics.FILE_STR -> return true
        else -> return false
    }
}