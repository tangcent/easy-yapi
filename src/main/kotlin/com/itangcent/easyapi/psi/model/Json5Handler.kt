package com.itangcent.easyapi.psi.model

import com.itangcent.easyapi.psi.type.JsonType

/**
 * Handler for generating JSON5 output with comments.
 *
 * JSON5 is an extension of JSON that supports:
 * - Single-line comments (//)
 * - Multi-line comments
 * - Trailing commas
 * - Unquoted keys
 *
 * Field comments are rendered as:
 * - End-of-line comments for simple types
 * - Block comments for complex types (arrays, maps, objects) or multi-line comments
 *
 * ## Output Example
 * ```json5
 * {
 *   name: "string", // User name
 *   /* User age */
 *   age: 0,
 *   active: false
 * }
 * ```
 *
 * @see RawJsonHandler for standard JSON output
 * @see ObjectModelJsonHandler for the interface
 */
object Json5Handler : ObjectModelJsonHandler {
    
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
        
        val fullComment = buildFullComment(field)
        if (!fullComment.isNullOrBlank()) {
            if (!canUseEndlineComment(field.model, fullComment)) {
                appendBlockComment(builder, fullComment.trim(), indent + 1)
            }
        }
        
        builder.append("  ".repeat(indent + 1))
        appendFieldName(builder, name)
        builder.append(": ")
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
        
        val fullComment = buildFullComment(field)
        if (!fullComment.isNullOrBlank()) {
            if (canUseEndlineComment(field.model, fullComment)) {
                builder.append(" // ")
                builder.append(fullComment.trim())
            }
        }
        
        builder.append("\n")
    }

    /**
     * Build the full comment text for a field, merging the description with option values.
     * Mirrors the legacy `KVUtils.getUltimateComment` behavior.
     */
    private fun buildFullComment(field: FieldModel): String? {
        val comment = field.comment
        val options = field.options
        if (options.isNullOrEmpty()) return comment

        val optionDesc = options
            .mapNotNull { opt ->
                val v = opt.value?.toString() ?: return@mapNotNull null
                if (opt.desc.isNullOrBlank()) v else "$v: ${opt.desc}"
            }
            .joinToString("\n")

        return when {
            optionDesc.isBlank() -> comment
            comment.isNullOrBlank() -> optionDesc
            else -> "$comment\n$optionDesc"
        }
    }
    
    private fun canUseEndlineComment(model: ObjectModel, comment: String?): Boolean {
        if (comment.isNullOrBlank()) return false
        if (comment.contains("\n")) return false
        return model is ObjectModel.Single
    }
    
    private fun appendBlockComment(builder: StringBuilder, comment: String, indent: Int) {
        val indentStr = "  ".repeat(indent)
        if (comment.contains("\n")) {
            val lines = comment.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n") { "$indentStr * $it" }
            builder.append("${indentStr}/*\n")
                .append(lines)
                .append("\n$indentStr */\n")
        } else {
            builder.append("${indentStr}/* $comment */\n")
        }
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
    
    private fun appendFieldName(builder: StringBuilder, name: String) {
        builder.append("\"$name\"")
    }
}
