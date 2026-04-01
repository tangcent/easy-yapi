package com.itangcent.easyapi.psi.model

import com.itangcent.easyapi.psi.type.JsonType

/**
 * Handler for generating standard JSON output.
 *
 * Produces valid JSON without comments or extensions.
 * Uses default values for types (e.g., "string" for string type, 0 for int).
 *
 * ## Output Example
 * ```json
 * {
 *   "name": "string",
 *   "age": 0,
 *   "active": false
 * }
 * ```
 *
 * @see Json5Handler for JSON5 with comments
 * @see ObjectModelJsonHandler for the interface
 */
object RawJsonHandler : ObjectModelJsonHandler {
    
    override fun beforeObjectStart(builder: StringBuilder, indent: Int) {
        builder.append("{")
    }
    
    override fun afterObjectEnd(builder: StringBuilder, indent: Int) {
        builder.append("  ".repeat(indent))
        builder.append("}")
    }
    
    override fun beforeObjectField(
        builder: StringBuilder,
        name: String,
        field: FieldModel,
        fieldIndex: Int,
        totalFields: Int,
        indent: Int
    ) {
        if (fieldIndex == 0) {
            builder.append("\n")
        }
        builder.append("  ".repeat(indent + 1))
        builder.append("\"$name\": ")
    }
    
    override fun afterObjectField(
        builder: StringBuilder,
        name: String,
        field: FieldModel,
        fieldIndex: Int,
        totalFields: Int,
        indent: Int
    ) {
        if (fieldIndex < totalFields - 1) {
            builder.append(",")
        }
        builder.append("\n")
    }
    
    override fun beforeArrayStart(builder: StringBuilder, indent: Int) {
        builder.append("[")
    }
    
    override fun afterArrayEnd(builder: StringBuilder, indent: Int) {
        builder.append("  ".repeat(indent))
        builder.append("]")
    }
    
    override fun beforeArrayItem(
        builder: StringBuilder,
        item: ObjectModel,
        itemIndex: Int,
        totalItems: Int,
        indent: Int
    ) {
        builder.append("\n")
        builder.append("  ".repeat(indent + 1))
    }
    
    override fun afterArrayItem(
        builder: StringBuilder,
        item: ObjectModel,
        itemIndex: Int,
        totalItems: Int,
        indent: Int
    ) {
        builder.append("\n")
    }
    
    override fun beforeMapStart(builder: StringBuilder, indent: Int) {
        builder.append("{")
    }
    
    override fun afterMapEnd(builder: StringBuilder, indent: Int) {
        builder.append("  ".repeat(indent))
        builder.append("}")
    }

    override fun beforeMapKey(builder: StringBuilder, indent: Int) {
        builder.append("\n")
        builder.append("  ".repeat(indent + 1))
    }

    override fun betweenMapKeyAndValue(builder: StringBuilder, indent: Int) {
        builder.append(": ")
    }

    override fun afterMapValue(builder: StringBuilder, indent: Int) {
        builder.append("\n")
    }
    
    override fun handleSingleValue(builder: StringBuilder, value: ObjectModel.Single, indent: Int) {
        val defaultValue = JsonType.defaultValueForType(value.type)
        when (defaultValue) {
            is String -> builder.append("\"$defaultValue\"")
            is Number, is Boolean -> builder.append(defaultValue)
            else -> builder.append("null")
        }
    }
}
