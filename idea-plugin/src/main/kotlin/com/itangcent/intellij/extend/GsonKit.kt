package com.itangcent.intellij.extend

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.itangcent.common.utils.GsonUtils

fun JsonObject.asMap(): HashMap<String, Any?> {
    val map: HashMap<String, Any?> = HashMap()
    this.entrySet().forEach { map[it.key] = it.value.unbox() }
    return map
}

fun JsonElement.asMap(dumb: Boolean = true): HashMap<String, Any?> {
    return when {
        this.isJsonObject -> this.asJsonObject.asMap()
        dumb -> HashMap()
        else -> throw IllegalStateException("Not a JSON Object: $this")
    }
}

fun JsonArray.asList(): ArrayList<Any?> {
    val list: ArrayList<Any?> = ArrayList()
    this.forEach { list.add(it.unbox()) }
    return list
}

fun JsonElement.asList(dumb: Boolean = true): ArrayList<Any?> {
    return when {
        this.isJsonArray -> this.asJsonArray.asList()
        dumb -> ArrayList()
        else -> throw IllegalStateException("Not a JSON Array: $this")
    }
}

fun JsonElement.unbox(): Any? {
    return when {
        this.isJsonNull -> null
        this.isJsonObject -> asMap()
        this.isJsonArray -> asList()
        this.isJsonPrimitive -> this.asJsonPrimitive.unbox()
        else -> null
    }
}

fun JsonPrimitive.unbox(): Any? {
    return when {
        this.isBoolean -> this.asBoolean
        this.isNumber -> this.asNumber
        this.isString -> this.asString
        else -> null
    }
}

fun String.asJsonElement(): JsonElement? {
    return GsonUtils.parseToJsonTree(this)
}

fun JsonElement?.sub(property: String): JsonElement? {
    if (this == null || !this.isJsonObject) {
        return null
    }
    return this.asJsonObject.get(property)
}
