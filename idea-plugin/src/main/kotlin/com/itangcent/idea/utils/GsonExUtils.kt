package com.itangcent.idea.utils

object GsonExUtils {
    fun resolveGsonLazily(str: String): String {
        if (str.contains("\"com.google.gson.internal.LazilyParsedNumber\"")) {
            return str.replace("\"com.google.gson.internal.LazilyParsedNumber\"", "\"java.lang.Integer\"")
        }
        return str
    }
}
