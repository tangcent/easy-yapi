package com.itangcent.easyapi.psi.model

import com.itangcent.easyapi.psi.type.JsonType

/**
 * Represents an option value for a field (e.g., enum constant, static field value).
 * Mirrors the legacy `{"value": ..., "desc": ...}` option maps.
 *
 * @param value The option value
 * @param desc Optional description of the option
 */
data class FieldOption(
    val value: Any?,
    val desc: String? = null
)

/**
 * Model for a field in an object structure.
 *
 * Contains the field's type model along with metadata like:
 * - Comment/description
 * - Required flag
 * - Default value
 * - Options (for enum-like fields)
 * - Mock and demo values
 * - Advanced properties
 *
 * @param model The type model for this field
 * @param comment Field description/comment
 * @param required Whether this field is required
 * @param defaultValue Default value for the field
 * @param options Available options (for enum fields)
 * @param mock Mock value for testing
 * @param demo Demo value for documentation
 * @param advanced Additional advanced properties
 */
data class FieldModel(
    val model: ObjectModel,
    val comment: String? = null,
    val required: Boolean = false,
    val defaultValue: String? = null,
    val options: List<FieldOption>? = null,
    val mock: String? = null,
    val demo: String? = null,
    val advanced: Map<String, Any?>? = null,
    /** True if this field's declared type is a generic type parameter (e.g., `T` in `Result<T>`) */
    val generic: Boolean = false
) {
    override fun toString(): String =
        "FieldModel(model=$model, comment=$comment, required=$required, defaultValue=$defaultValue, generic=$generic)"
}

/**
 * Sealed class representing a JSON object model.
 *
 * Models the structure of JSON data types:
 * - [Single] - Primitive or simple types (string, number, boolean, null)
 * - [Object] - Object with named fields
 * - [Array] - Array of items
 * - [MapModel] - Map/dictionary with key-value pairs
 *
 * Used for:
 * - Request/response body modeling
 * - JSON schema generation
 * - Example value creation
 *
 * ## Example
 * ```kotlin
 * // Build a simple object
 * val model = ObjectModel.Object(mapOf(
 *     "name" to FieldModel(ObjectModel.single("string")),
 *     "age" to FieldModel(ObjectModel.single("int"))
 * ))
 * ```
 */
sealed class ObjectModel {
    /**
     * A single/primitive type value.
     *
     * @param type The JSON type name (string, int, boolean, etc.)
     */
    data class Single(
        val type: String
    ) : ObjectModel()

    /**
     * An object with named fields.
     *
     * @param fields Map of field names to their models
     * @param id Unique identifier for this object instance
     */
    data class Object(
        val fields: Map<String, FieldModel>,
        val id: Int = nextId++
    ) : ObjectModel() {
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Object) return false
            return id == other.id
        }
        
        override fun hashCode(): Int = id.hashCode()
        
        override fun toString(): String = "Object(id=$id, fields=${fields.keys})"
        
        companion object {
            private var nextId = 0
        }

        fun flattenFields(
            prefix: String = "",
            maxDepth: Int = 5,
            currentDepth: Int = 0
        ): Map<String, FieldModel> {
            if (currentDepth >= maxDepth) return emptyMap()
            
            val result = linkedMapOf<String, FieldModel>()
            
            for ((fieldName, fieldModel) in fields) {
                val fieldPath = if (prefix.isEmpty()) fieldName else "$prefix.$fieldName"
                
                when (val model = fieldModel.model) {
                    is ObjectModel.Object -> {
                        val nestedFields = model.flattenFields(fieldPath, maxDepth, currentDepth + 1)
                        result.putAll(nestedFields)
                    }
                    is ObjectModel.Array -> {
                        val itemPath = "$fieldPath[0]"
                        flattenArrayField(itemPath, model.item, fieldModel, result, maxDepth, currentDepth + 1)
                    }
                    else -> {
                        result[fieldPath] = fieldModel
                    }
                }
            }
            
            return result
        }
        
        private fun flattenArrayField(
            itemPath: String,
            itemModel: ObjectModel,
            fieldModel: FieldModel,
            result: MutableMap<String, FieldModel>,
            maxDepth: Int,
            currentDepth: Int
        ) {
            if (currentDepth >= maxDepth) return
            
            when (itemModel) {
                is Object -> {
                    val nestedFields = itemModel.flattenFields(itemPath, maxDepth, currentDepth + 1)
                    result.putAll(nestedFields)
                }
                is Array -> {
                    val nestedItemPath = "$itemPath[0]"
                    flattenArrayField(nestedItemPath, itemModel.item, fieldModel, result, maxDepth, currentDepth + 1)
                }
                else -> {
                    result[itemPath] = fieldModel
                }
            }
        }
    }

    data class Array(
        val item: ObjectModel
    ) : ObjectModel()

    data class MapModel(
        val keyType: ObjectModel,
        val valueType: ObjectModel
    ) : ObjectModel()

    fun isSingle(): Boolean = this is Single
    fun isObject(): Boolean = this is Object
    fun isArray(): Boolean = this is Array
    fun isMap(): Boolean = this is MapModel

    fun asSingle(): Single? = this as? Single
    fun asObject(): Object? = this as? Object
    fun asArray(): Array? = this as? Array
    fun asMap(): MapModel? = this as? MapModel

    companion object {
        fun emptyObject(): Object = Object(emptyMap())
        fun nullValue(): Single = Single("null")
        fun single(type: String): Single = Single(type)
        fun array(itemType: ObjectModel): Array = Array(itemType)
        fun map(keyType: ObjectModel, valueType: ObjectModel): MapModel = MapModel(keyType, valueType)
    }
}

class ObjectModelBuilder {
    private val fields = linkedMapOf<String, FieldModel>()

    fun field(
        name: String,
        model: ObjectModel,
        comment: String? = null,
        required: Boolean = false,
        defaultValue: String? = null,
        options: List<FieldOption>? = null,
        mock: String? = null,
        demo: String? = null,
        advanced: Map<String, Any?>? = null
    ): ObjectModelBuilder = apply {
        fields[name] = FieldModel(model, comment, required, defaultValue, options, mock, demo, advanced)
    }

    fun stringField(
        name: String,
        comment: String? = null,
        required: Boolean = false,
        defaultValue: String? = null
    ): ObjectModelBuilder = apply {
        fields[name] = FieldModel(ObjectModel.single(JsonType.STRING), comment, required, defaultValue)
    }

    fun intField(
        name: String,
        comment: String? = null,
        required: Boolean = false,
        defaultValue: String? = null
    ): ObjectModelBuilder = apply {
        fields[name] = FieldModel(ObjectModel.single(JsonType.INT), comment, required, defaultValue)
    }

    fun longField(
        name: String,
        comment: String? = null,
        required: Boolean = false,
        defaultValue: String? = null
    ): ObjectModelBuilder = apply {
        fields[name] = FieldModel(ObjectModel.single(JsonType.LONG), comment, required, defaultValue)
    }

    fun floatField(
        name: String,
        comment: String? = null,
        required: Boolean = false,
        defaultValue: String? = null
    ): ObjectModelBuilder = apply {
        fields[name] = FieldModel(ObjectModel.single(JsonType.FLOAT), comment, required, defaultValue)
    }

    fun doubleField(
        name: String,
        comment: String? = null,
        required: Boolean = false,
        defaultValue: String? = null
    ): ObjectModelBuilder = apply {
        fields[name] = FieldModel(ObjectModel.single(JsonType.DOUBLE), comment, required, defaultValue)
    }

    fun booleanField(
        name: String,
        comment: String? = null,
        required: Boolean = false,
        defaultValue: String? = null
    ): ObjectModelBuilder = apply {
        fields[name] = FieldModel(ObjectModel.single(JsonType.BOOLEAN), comment, required, defaultValue)
    }

    fun arrayField(
        name: String,
        itemType: ObjectModel,
        comment: String? = null,
        required: Boolean = false,
        defaultValue: String? = null
    ): ObjectModelBuilder = apply {
        fields[name] = FieldModel(ObjectModel.array(itemType), comment, required, defaultValue)
    }

    fun objectField(
        name: String,
        objectModel: ObjectModel.Object,
        comment: String? = null,
        required: Boolean = false,
        defaultValue: String? = null
    ): ObjectModelBuilder = apply {
        fields[name] = FieldModel(objectModel, comment, required, defaultValue)
    }

    fun mapField(
        name: String,
        keyType: ObjectModel,
        valueType: ObjectModel,
        comment: String? = null,
        required: Boolean = false,
        defaultValue: String? = null
    ): ObjectModelBuilder = apply {
        fields[name] = FieldModel(ObjectModel.map(keyType, valueType), comment, required, defaultValue)
    }

    fun build(): ObjectModel.Object = ObjectModel.Object(fields)
}
