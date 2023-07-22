package com.itangcent.utils

import com.itangcent.common.constant.Attrs
import com.itangcent.common.utils.Extensible
import com.itangcent.common.utils.GsonUtils
import com.itangcent.common.utils.mapToTypedArray
import com.itangcent.intellij.extend.unbox
import kotlin.reflect.KClass

object ExtensibleKit {

    private val GSON = com.google.gson.Gson()

    fun <T : Extensible> KClass<T>.fromJson(json: String): T {
        val jsonElement = GsonUtils.parseToJsonTree(json)!!
        val t = GSON.fromJson(jsonElement, this.java)
        jsonElement.asJsonObject.entrySet()
            .filter { it.key.startsWith(Attrs.PREFIX) }
            .forEach { t.setExt(it.key, it.value.unbox()) }
        return t
    }

    fun <T : Extensible> KClass<T>.fromJson(json: String, vararg exts: String): T {
        val extNames = exts.toSet() +
                exts.map { it.removePrefix(Attrs.PREFIX) } +
                exts.map { it.addPrefix(Attrs.PREFIX) }
        val jsonElement = GsonUtils.parseToJsonTree(json)!!
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

}

fun Extensible.setExts(exts: Map<String, Any?>) {
    exts.forEach { (t, u) ->
        this.setExt(t, u)
    }
}
