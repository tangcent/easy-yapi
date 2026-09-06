package com.itangcent.easyapi.core.util.json

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
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
     *
     * Deliberately does **not** serialize nulls (the default; there is no
     * `serializeNulls(false)` in Gson — omitting the call is the explicit "off").
     * The compact form is used to persist settings and other data where a null field
     * is indistinguishable from an absent field, and several readers
     * (`SettingsPanels.applyImported`) rely on null fields being dropped rather than
     * emitted as `JsonNull`.
     */
    val GSON: Gson = GsonBuilder()
        .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
        .create()

    /**
     * Gson instance with pretty printing enabled.
     *
     * Serializes null values so that a field explicitly set to `null` keeps its key in
     * the output, rather than being silently dropped (required by JSON body merging and
     * formatting, see `EndpointDetailsPanelLogic.prettyJson`).
     */
    val PRETTY: Gson = GsonBuilder()
        .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
        .setPrettyPrinting()
        .serializeNulls()
        .create()

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
