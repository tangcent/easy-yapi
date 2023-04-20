package com.itangcent.idea.utils

import com.itangcent.common.utils.GsonUtils

@Deprecated(replaceWith = ReplaceWith("GsonUtils"), message = "use GsonUtils")
typealias GsonExUtils = GsonUtils

fun GsonUtils.prettyJsonStr(json: String): String {
    val jsonObject = parseToJsonTree(json)
    return prettyJson(jsonObject)
}

fun String.resolveGsonLazily(): String {
    if (this.contains("\"com.google.gson.internal.LazilyParsedNumber\"")) {
        return this.replace("\"com.google.gson.internal.LazilyParsedNumber\"", "\"java.lang.Integer\"")
    }
    return this
}