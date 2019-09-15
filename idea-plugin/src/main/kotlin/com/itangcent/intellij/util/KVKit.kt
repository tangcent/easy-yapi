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