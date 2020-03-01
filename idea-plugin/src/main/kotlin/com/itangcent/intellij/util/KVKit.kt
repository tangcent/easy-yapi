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
    this.forEach { k, v ->
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

@Suppress("UNCHECKED_CAST")
fun <T> Map<*, *>.getAs(key: Any?): T? {
    return this[key] as? T
}

@Suppress("UNCHECKED_CAST")
fun <T> Map<*, *>.getAs(key: Any?, subKey: Any?): T? {
    return this.getAs<Map<*, *>>(key)?.getAs(subKey)
}