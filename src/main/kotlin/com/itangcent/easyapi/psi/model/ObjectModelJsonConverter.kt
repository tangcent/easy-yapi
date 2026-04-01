package com.itangcent.easyapi.psi.model

/**
 * Converts ObjectModel structures to JSON strings.
 *
 * Provides convenience methods for common JSON formats:
 * - Standard JSON (toJson)
 * - JSON5 with comments (toJson5)
 * - Custom format (toJson with handler)
 *
 * ## Usage
 * ```kotlin
 * val model = ObjectModel.Object(mapOf(
 *     "name" to FieldModel(ObjectModel.single("string"), comment = "User name")
 * ))
 *
 * // Standard JSON
 * val json = ObjectModelJsonConverter.toJson(model)
 * // {"name": "string"}
 *
 * // JSON5 with comments
 * val json5 = ObjectModelJsonConverter.toJson5(model)
 * // {"name": "string" /*User name*/}
 * ```
 *
 * @see ObjectModelJsonBuilder for the underlying builder
 * @see ObjectModelJsonHandler for custom formats
 */
object ObjectModelJsonConverter {
    
    fun toJson(model: ObjectModel?): String {
        return ObjectModelJsonBuilder(RawJsonHandler).build(model)
    }
    
    fun toJson5(model: ObjectModel?): String {
        return ObjectModelJsonBuilder(Json5Handler).build(model)
    }
    
    fun toJson(model: ObjectModel?, handler: ObjectModelJsonHandler): String {
        return ObjectModelJsonBuilder(handler).build(model)
    }
}
