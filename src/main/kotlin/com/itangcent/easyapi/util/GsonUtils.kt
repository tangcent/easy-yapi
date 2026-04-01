package com.itangcent.easyapi.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/**
 * Utility object for JSON serialization and deserialization using Gson.
 *
 * Provides:
 * - Standard Gson instance for compact JSON
 * - Pretty-printing Gson instance for formatted output
 * - Type-safe deserialization methods
 *
 * ## Usage
 * ```kotlin
 * // Serialize to JSON
 * val json = GsonUtils.toJson(myObject)
 * val pretty = GsonUtils.prettyJson(myObject)
 *
 * // Deserialize from JSON
 * val obj = GsonUtils.fromJson<MyClass>(jsonString)
 * ```
 */
object GsonUtils {
    /**
     * Standard Gson instance for compact JSON output.
     */
    val GSON: Gson = GsonBuilder().create()

    /**
     * Gson instance with pretty printing enabled.
     */
    val PRETTY: Gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Serializes an object to compact JSON.
     */
    fun toJson(obj: Any?): String = GSON.toJson(obj)

    /**
     * Serializes an object to pretty-printed JSON.
     */
    fun prettyJson(obj: Any?): String = PRETTY.toJson(obj)

    /**
     * Deserializes JSON to a reified type.
     */
    inline fun <reified T> fromJson(json: String): T = fromJson(json, object : TypeToken<T>() {}.type)

    /**
     * Deserializes JSON to a specific type.
     */
    fun <T> fromJson(json: String, type: Type): T = GSON.fromJson(json, type)
}
