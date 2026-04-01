package com.itangcent.easyapi.psi.model

import com.itangcent.easyapi.psi.type.JsonType

/**
 * Converts ObjectModel structures to simple Kotlin values.
 *
 * Transforms ObjectModel types into their corresponding Kotlin primitive/collection representations:
 * - [ObjectModel.Single] -> primitive values (String, Int, Long, Boolean, etc.)
 * - [ObjectModel.Object] -> Map<String, Any?>
 * - [ObjectModel.Array] -> List<Any?>
 * - [ObjectModel.MapModel] -> Map<String, Any?>
 *
 * Handles circular references by tracking visited [ObjectModel.Object] instances via their unique [id][ObjectModel.Object.id].
 * When a circular reference is detected, an empty map is returned for that object.
 *
 * @see ObjectModel for the model structure
 * @see ObjectModelJsonBuilder for JSON string output
 */
object ObjectModelValueConverter {
    
    /**
     * Converts an ObjectModel to its corresponding Kotlin value representation.
     *
     * @param model The ObjectModel to convert, or null
     * @return The corresponding Kotlin value, or null if the input is null
     */
    fun toSimpleValue(model: ObjectModel?): Any? {
        if (model == null) return null
        val visited = HashSet<Int>()
        return toSimpleValue(model, visited)
    }
    
    private fun toSimpleValue(
        model: ObjectModel,
        visited: HashSet<Int>
    ): Any? {
        return when (model) {
            is ObjectModel.Single -> singleToValue(model)
            is ObjectModel.Object -> objectToMap(model, visited)
            is ObjectModel.Array -> arrayToList(model, visited)
            is ObjectModel.MapModel -> mapToValue(model, visited)
        }
    }
    
    /**
     * Converts a Single type to its default primitive value.
     */
    private fun singleToValue(single: ObjectModel.Single): Any? {
        return when (single.type) {
            JsonType.STRING -> ""
            JsonType.INT, JsonType.SHORT -> 0
            JsonType.LONG -> 0L
            JsonType.FLOAT -> 0.0f
            JsonType.DOUBLE -> 0.0
            JsonType.BOOLEAN -> false
            JsonType.DATE -> ""
            JsonType.DATETIME -> ""
            JsonType.FILE -> "(binary)"
            JsonType.OBJECT -> emptyMap<String, Any?>()
            JsonType.ARRAY -> emptyList<Any?>()
            "null" -> null
            else -> null
        }
    }
    
    /**
     * Converts an Object to a Map, tracking visited objects to detect circular references.
     */
    private fun objectToMap(
        model: ObjectModel.Object,
        visited: HashSet<Int>
    ): Map<String, Any?> {
        if (!visited.add(model.id)) {
            return emptyMap()
        }
        return model.fields.mapValues { (_, field) -> 
            toSimpleValue(field.model, visited) 
        }
    }
    
    /**
     * Converts an Array to a List containing a single item representation.
     */
    private fun arrayToList(
        model: ObjectModel.Array,
        visited: HashSet<Int>
    ): List<Any?> {
        return listOf(toSimpleValue(model.item, visited))
    }
    
    /**
     * Converts a MapModel to a Map with an empty string key.
     */
    private fun mapToValue(
        model: ObjectModel.MapModel,
        visited: HashSet<Int>
    ): Map<String, Any?> {
        return mapOf("" to toSimpleValue(model.valueType, visited))
    }
}
