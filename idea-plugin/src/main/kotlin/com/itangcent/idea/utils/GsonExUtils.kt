package com.itangcent.idea.utils

import com.google.gson.JsonParser
import com.itangcent.common.utils.GsonUtils

@Deprecated(replaceWith = ReplaceWith("GsonUtils"), message = "use GsonUtils")
typealias GsonExUtils = GsonUtils

fun GsonUtils.prettyJsonStr(json: String): String {
    val jsonParser = JsonParser()
    val jsonObject = jsonParser.parse(json).asJsonObject
    return GsonUtils.prettyJson(jsonObject)
}

fun String.resolveGsonLazily(): String {
    if (this.contains("\"com.google.gson.internal.LazilyParsedNumber\"")) {
        return this.replace("\"com.google.gson.internal.LazilyParsedNumber\"", "\"java.lang.Integer\"")
    }
    return this
}