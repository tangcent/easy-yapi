package com.itangcent.intellij.extend

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

fun JsonObject.asMap(): HashMap<String, Any?> {
    val map: HashMap<String, Any?> = HashMap()
    this.entrySet().forEach { map[it.key] = it.value.unbox() }
    return map
}

fun JsonElement.asMap(): HashMap<String, Any?> {
    return this.asJsonObject.asMap()
}

fun JsonArray.asList(): ArrayList<Any?> {
    val list: ArrayList<Any?> = ArrayList()
    this.forEach { list.add(it.unbox()) }
    return list
}

fun JsonElement.asList(): ArrayList<Any?> {
    return this.asJsonArray.asList()
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
        this.isJsonNull -> null
        this.isBoolean -> this.asBoolean
        this.isNumber -> this.asNumber
        this.isString -> this.asString
        else -> null
    }
}