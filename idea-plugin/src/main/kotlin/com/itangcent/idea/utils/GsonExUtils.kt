package com.itangcent.idea.utils

import com.google.gson.JsonParser
import com.itangcent.common.utils.GsonUtils

object GsonExUtils {

    fun toJson(bean: Any?): String {
        val beanWithClass = BeanWithClass()
        if (bean != null) {
            beanWithClass.c = bean.javaClass.name
            beanWithClass.j = GsonUtils.toJson(bean)
        }
        return GsonUtils.toJson(beanWithClass)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> fromJson(json: String): T? {
        val beanWithClass = GsonUtils.fromJson(json, BeanWithClass::class)
        if (beanWithClass.c == null) return null
        val cls: Class<T> = Class.forName(beanWithClass.c) as Class<T>
        return GsonUtils.fromJson(beanWithClass.j!!, cls)
    }

    class BeanWithClass {
        //class
        var c: String? = null
        //json
        var j: String? = null
    }

    fun prettyJson(json: String): String {
        val jsonParser = JsonParser()
        val jsonObject = jsonParser.parse(json).asJsonObject
        return GsonUtils.prettyJson(jsonObject)
    }
}