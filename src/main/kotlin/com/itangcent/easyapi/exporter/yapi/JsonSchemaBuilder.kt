package com.itangcent.easyapi.exporter.yapi

import com.itangcent.easyapi.exporter.model.ApiParameter
import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.JsonType
import com.itangcent.easyapi.util.GsonUtils
import java.util.IdentityHashMap

/**
 * Builder for creating JSON Schema from object models.
 * 
 * This builder converts the internal ObjectModel representation into
 * JSON Schema format compatible with YAPI. It supports:
 * - All JSON primitive types (string, number, integer, boolean)
 * - Nested objects and arrays
 * - Map types with additionalProperties
 * - Field descriptions, defaults, and mock data
 * - Enum values with descriptions
 * - Circular reference detection
 * 
 * @param maxVisits Maximum times to visit the same object (prevents infinite recursion)
 */
class JsonSchemaBuilder(private val maxVisits: Int = MAX_VISITS) {

    /** Configuration constants */
    companion object {
        private const val MAX_VISITS = 2
        private const val SCHEMA_URL = "http://json-schema.org/draft-04/schema#"
    }

    /**
     * Builds a complete JSON Schema string from an ObjectModel.
     * Includes the $schema header for root-level schemas.
     * 
     * @param model The object model to convert
     * @param rootDesc Optional root-level description
     * @return A JSON Schema string
     */
    fun build(model: ObjectModel?, rootDesc: String? = null): String {
        val schema = if (model == null) {
            linkedMapOf<String, Any?>("type" to "object")
        } else {
            buildSchema(model)
        }
        // Add root-level $schema header
        val result = linkedMapOf<String, Any?>("\$schema" to SCHEMA_URL)
        if (rootDesc != null) {
            result["description"] = rootDesc
        }
        result.putAll(schema)
        return GsonUtils.toJson(result)
    }

    /**
     * Builds a JSON Schema map without the $schema header.
     * Useful for nested schemas that shouldn't have the header.
     * 
     * @param model The object model to convert
     * @return A map representing the JSON Schema
     */
    fun buildAsMap(model: ObjectModel?): Map<String, Any?> {
        if (model == null) {
            return linkedMapOf("type" to "object")
        }
        return buildSchema(model)
    }

    /**
     * Builds a JSON Schema from a list of API parameters.
     * Creates an object schema with properties for each parameter.
     * 
     * @param params The list of API parameters
     * @return A JSON Schema string
     */
    fun buildFromParameters(params: List<ApiParameter>): String {
        val properties = linkedMapOf<String, Any?>()
        val required = mutableListOf<String>()

        params.forEach { param ->
            val propSchema = linkedMapOf<String, Any?>()

            param.type?.let { type ->
                propSchema["type"] = mapJsonTypeToSchemaType(type)
            }

            param.description?.let { desc ->
                propSchema["description"] = desc
            }

            param.example?.let { example ->
                propSchema["example"] = example
            }

            param.enumValues?.let { enums ->
                if (enums.isNotEmpty()) {
                    propSchema["enum"] = enums
                }
            }

            properties[param.name] = propSchema

            if (param.required) {
                required.add(param.name)
            }
        }

        val result = linkedMapOf<String, Any?>(
            "\$schema" to SCHEMA_URL,
            "type" to "object",
            "properties" to properties
        )

        if (required.isNotEmpty()) {
            result["required"] = required
        }

        return GsonUtils.toJson(result)
    }

    /**
     * Builds a schema map from any object model type.
     * Dispatches to the appropriate type-specific builder.
     * 
     * @param model The object model
     * @param visitCounts Tracking map for circular reference detection
     * @return A schema map
     */
    private fun buildSchema(
        model: ObjectModel,
        visitCounts: IdentityHashMap<ObjectModel.Object, Int> = IdentityHashMap()
    ): LinkedHashMap<String, Any?> {
        return when (model) {
            is ObjectModel.Single -> buildSingleSchema(model)
            is ObjectModel.Object -> buildObjectSchema(model, visitCounts)
            is ObjectModel.Array -> buildArraySchema(model, visitCounts)
            is ObjectModel.MapModel -> buildMapSchema(model, visitCounts)
        }
    }

    /**
     * Builds a schema for a single primitive value.
     * 
     * @param model The single value model
     * @return A schema map with type
     */
    private fun buildSingleSchema(model: ObjectModel.Single): LinkedHashMap<String, Any?> {
        return linkedMapOf("type" to mapJsonTypeToSchemaType(model.type))
    }

    /**
     * Builds a schema for an object type.
     * Handles properties, required fields, descriptions, defaults, and enums.
     * Implements circular reference detection using visit counts.
     * 
     * @param model The object model
     * @param visitCounts Tracking map for circular reference detection
     * @return A schema map with type, properties, and required
     */
    private fun buildObjectSchema(
        model: ObjectModel.Object,
        visitCounts: IdentityHashMap<ObjectModel.Object, Int>
    ): LinkedHashMap<String, Any?> {
        val count = visitCounts.getOrDefault(model, 0)
        if (count >= maxVisits) {
            return linkedMapOf("type" to "object")
        }
        visitCounts[model] = count + 1

        try {
            val properties = linkedMapOf<String, Any?>()
            val requiredList = mutableListOf<String>()

            for ((name, field) in model.fields) {
                val propSchema = buildSchema(field.model, visitCounts)

                // Add description from field comment
                field.comment?.let { propSchema["description"] = it }

                // Add default value
                field.defaultValue?.takeIf { it.isNotBlank() }?.let { propSchema["default"] = it }

                // Add mock data (yapi format: {"mock": {"mock": "value"}})
                field.mock?.let { mockStr ->
                    propSchema["mock"] = linkedMapOf("mock" to mockStr)
                }

                // Add enum values and descriptions from options
                field.options?.takeIf { it.isNotEmpty() }?.let { options ->
                    // For arrays, drill into items to set enum
                    var targetSchema = propSchema
                    while (targetSchema["type"] == "array") {
                        @Suppress("UNCHECKED_CAST")
                        val items = targetSchema["items"] as? LinkedHashMap<String, Any?> ?: break
                        targetSchema = items
                    }

                    val enumValues = options.map { it.value }
                    targetSchema["enum"] = enumValues

                    val enumDesc = options
                        .filter { !it.desc.isNullOrBlank() }
                        .joinToString("\n") { "${it.value}: ${it.desc}" }
                    if (enumDesc.isNotBlank()) {
                        targetSchema["enumDesc"] = enumDesc
                    }

                    // Add mock for enum pick
                    if (!targetSchema.containsKey("mock")) {
                        targetSchema["mock"] = linkedMapOf("mock" to "@pick(${GsonUtils.toJson(enumValues)})")
                    }
                }

                // Add advanced properties
                field.advanced?.let { advanced ->
                    for ((key, value) in advanced) {
                        propSchema.putIfAbsent(key, value)
                    }
                }

                properties[name] = propSchema

                if (field.required) {
                    requiredList.add(name)
                }
            }

            val result = linkedMapOf<String, Any?>(
                "type" to "object",
                "properties" to properties
            )

            if (requiredList.isNotEmpty()) {
                result["required"] = requiredList
            }

            return result
        } finally {
            visitCounts[model] = count
        }
    }

    /**
     * Builds a schema for an array type.
     * 
     * @param model The array model
     * @param visitCounts Tracking map for circular reference detection
     * @return A schema map with type and items
     */
    private fun buildArraySchema(
        model: ObjectModel.Array,
        visitCounts: IdentityHashMap<ObjectModel.Object, Int>
    ): LinkedHashMap<String, Any?> {
        return linkedMapOf(
            "type" to "array",
            "items" to buildSchema(model.item, visitCounts)
        )
    }

    /**
     * Builds a schema for a map/dictionary type.
     * Uses additionalProperties to define value types.
     * 
     * @param model The map model
     * @param visitCounts Tracking map for circular reference detection
     * @return A schema map with type and additionalProperties
     */
    private fun buildMapSchema(
        model: ObjectModel.MapModel,
        visitCounts: IdentityHashMap<ObjectModel.Object, Int>
    ): LinkedHashMap<String, Any?> {
        return linkedMapOf(
            "type" to "object",
            "additionalProperties" to buildSchema(model.valueType, visitCounts)
        )
    }

    /**
     * Maps a JSON type string to JSON Schema type.
     * Handles standard JSON types plus date/datetime extensions.
     * 
     * @param type The JSON type string
     * @return The corresponding JSON Schema type
     */
    private fun mapJsonTypeToSchemaType(type: String): String {
        return when (type) {
            JsonType.STRING, JsonType.DATE, JsonType.DATETIME, JsonType.FILE -> "string"
            JsonType.SHORT, JsonType.INT, JsonType.LONG -> "integer"
            JsonType.FLOAT, JsonType.DOUBLE -> "number"
            JsonType.BOOLEAN -> "boolean"
            JsonType.ARRAY -> "array"
            JsonType.OBJECT -> "object"
            "null" -> "null"
            else -> "string"
        }
    }
}
