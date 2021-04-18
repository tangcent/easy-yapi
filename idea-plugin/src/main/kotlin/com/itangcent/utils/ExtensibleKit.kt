package com.itangcent.utils

import com.itangcent.common.constant.Attrs
import com.itangcent.common.utils.Extensible
import com.itangcent.common.utils.mapToTypedArray
import com.itangcent.intellij.extend.unbox
import kotlin.reflect.KClass

object ExtensibleKit {

    private val JSON_PARSER = com.google.gson.JsonParser()
    private val GSON = com.google.gson.Gson()

    fun <T : Extensible> KClass<T>.fromJson(json: String): T {
        val jsonElement = JSON_PARSER.parse(json)
        val t = GSON.fromJson(jsonElement, this.java)
        jsonElement.asJsonObject.entrySet()
                .filter { it.key.startsWith(Attrs.PREFIX) }
                .forEach { t.setExt(it.key, it.value.unbox()) }
        return t
    }

    fun <T : Extensible> KClass<T>.fromJson(json: String, vararg exts: String): T {
        val extNames = exts.removePrefix(Attrs.PREFIX)
        val jsonElement = JSON_PARSER.parse(json)
        val t = GSON.fromJson(jsonElement, this.java)
        jsonElement.asJsonObject.entrySet()
                .filter { extNames.contains(it.key) }
                .forEach { t.setExt(it.key.addPrefix(Attrs.PREFIX), it.value.unbox()) }
        return t
    }

    /**
     * If this string not starts with the given [prefix], returns a copy of this string
     * with the prefix. Otherwise, returns this string.
     */
    private fun String.addPrefix(prefix: String): String {
        if (!startsWith(prefix)) {
            return prefix + this
        }
        return this
    }

    /**
     * Remove [prefix] from each string in the original array.
     */
    private fun Array<out String>.removePrefix(prefix: CharSequence): Array<String> {
        return this.mapToTypedArray { it.removePrefix(prefix) }
    }
}
