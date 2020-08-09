package com.itangcent.idea.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.TypeAdapterFactory
import com.google.gson.internal.bind.ObjectTypeAdapter
import com.itangcent.common.utils.GsonUtils
import com.itangcent.common.utils.RegisterExclusionStrategy
import com.itangcent.common.utils.Visional
import com.itangcent.intellij.extend.getPropertyValue

object GsonExUtils {

    private val gson: Gson

    init {
        val numberObjectTypeAdapter = NumberFixedObjectTypeAdapter()
        gson = GsonBuilder()
                .setExclusionStrategies(RegisterExclusionStrategy().exclude(Visional::class.java))
                .registerTypeAdapterFactory(NumberFixedObjectTypeAdapter.FACTORY)
                .registerTypeAdapter(Any::class.java, numberObjectTypeAdapter)
                .registerTypeAdapter(Map::class.java, numberObjectTypeAdapter)
                .registerTypeAdapter(List::class.java, numberObjectTypeAdapter)
                .create()
        numberObjectTypeAdapter.setGson(gson)

        try {
            formatGson(gson)
        } catch (e: Exception) {
            //I am very sad
            //But there is no plan B to fix it.
        }

    }

    /**
     * try remove default ObjectTypeAdapter from gson
     * if success,the {@link NumberFixedObjectTypeAdapter} will be used instead
     */
    @Suppress("UNCHECKED_CAST")
    private fun formatGson(gson: Gson) {

        var factories = gson.getPropertyValue("factories") ?: return

        //unwrap for UnmodifiableList
        if (factories::class.qualifiedName == "java.util.Collections.UnmodifiableRandomAccessList" ||
                factories::class.qualifiedName == "java.util.Collections.UnmodifiableList") {
            factories = factories.getPropertyValue("list") ?: return
        }

        (factories as MutableList<TypeAdapterFactory>).remove(ObjectTypeAdapter.FACTORY)
    }

    fun toJson(bean: Any?): String {
        val beanWithClass = BeanWithClass()
        if (bean != null) {
            beanWithClass.c = bean.javaClass.name
            beanWithClass.j = gson.toJson(bean)
        }
        return gson.toJson(beanWithClass)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> fromJson(json: String): T? {
        val beanWithClass = gson.fromJson(json, BeanWithClass::class.java)
        if (beanWithClass.c == null) return null
        val cls: Class<T> = Class.forName(beanWithClass.c) as Class<T>
        return gson.fromJson(beanWithClass.j!!, cls)
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

