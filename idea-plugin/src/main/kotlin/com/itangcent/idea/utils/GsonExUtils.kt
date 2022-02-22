package com.itangcent.idea.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.TypeAdapterFactory
import com.google.gson.internal.LazilyParsedNumber
import com.google.gson.internal.bind.ObjectTypeAdapter
import com.itangcent.common.utils.GsonUtils
import com.itangcent.common.utils.RegisterExclusionStrategy
import com.itangcent.common.utils.Visional
import com.itangcent.common.utils.getPropertyValue

object GsonExUtils {

    val gson: Gson

    init {
        val numberObjectTypeAdapter = NumberFixedObjectTypeAdapter()
        gson = GsonBuilder()
            .setExclusionStrategies(RegisterExclusionStrategy().exclude(Visional::class.java))
            .registerTypeAdapterFactory(NumberFixedObjectTypeAdapter.FACTORY)
            .registerTypeAdapter(Any::class.java, numberObjectTypeAdapter)
            .registerTypeAdapter(Map::class.java, numberObjectTypeAdapter)
            .registerTypeAdapter(List::class.java, numberObjectTypeAdapter)
            .registerTypeAdapter(LazilyParsedNumber::class.java, LazilyParsedNumberTypeAdapter())
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
            factories::class.qualifiedName == "java.util.Collections.UnmodifiableList"
        ) {
            factories = factories.getPropertyValue("list") ?: return
        }
        val objectTypeAdapterClassName = ObjectTypeAdapter::class.qualifiedName!!
        (factories as MutableList<TypeAdapterFactory>).removeIf {
            it::class.java.name.startsWith(objectTypeAdapterClassName)
        }
    }

    fun toJson(bean: Any?): String {
        return gson.toJson(bean)
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> fromJson(json: String): T? {
        return gson.fromJson(json, T::class.java)
    }

    fun prettyJson(json: String): String {
        val jsonParser = JsonParser()
        val jsonObject = jsonParser.parse(json).asJsonObject
        return GsonUtils.prettyJson(jsonObject)
    }
}


fun String.resolveGsonLazily(): String {
    if (this.contains("\"com.google.gson.internal.LazilyParsedNumber\"")) {
        return this.replace("\"com.google.gson.internal.LazilyParsedNumber\"", "\"java.lang.Integer\"")
    }
    return this
}