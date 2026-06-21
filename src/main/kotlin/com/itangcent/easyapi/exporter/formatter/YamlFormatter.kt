package com.itangcent.easyapi.exporter.formatter

import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.JsonType
import java.util.IdentityHashMap

/**
 * Renders an [ObjectModel] as YAML (block style, 2-space indent).
 *
 * Produces clean YAML with default values per type — matching the convention
 * of [ObjectModelJsonConverter] (JSON/JSON5) and [PropertiesFormatter]:
 * - string → `""`
 * - number → `0`
 * - boolean → `false`
 * - unknown → `null`
 *
 * ## Output Format
 * ```yaml
 * id: 0
 * name: ""
 * active: false
 * tags:
 *   - ""
 * nested:
 *   id: 0
 * ```
 *
 * ## Recursive Reference Handling
 * To prevent infinite loops with self-referencing types, each object
 * is tracked and limited to [MAX_VISITS] expansions — same pattern as
 * [PropertiesFormatter].
 *
 * ## Usage
 * ```kotlin
 * val yaml = YamlFormatter.format(objectModel)
 * ```
 *
 * @see ObjectModel for the input model
 */
object YamlFormatter {

    private const val INDENT = "  "
    private const val MAX_VISITS = 2

    /** YAML rendering of an [ObjectModel]. */
    fun format(model: ObjectModel): String {
        val sb = StringBuilder()
        val visitCounts = IdentityHashMap<ObjectModel.Object, Int>()
        renderTop(model, sb, visitCounts)
        return sb.toString().trimEnd()
    }

    private fun renderTop(
        model: ObjectModel,
        sb: StringBuilder,
        visitCounts: IdentityHashMap<ObjectModel.Object, Int>
    ) {
        when (model) {
            is ObjectModel.Object -> renderObjectFields(model, 0, sb, visitCounts, firstLine = true)
            is ObjectModel.Array -> renderArray(model, 0, sb, visitCounts)
            is ObjectModel.MapModel -> renderMap(model, 0, sb, visitCounts)
            is ObjectModel.Single -> sb.append("value: ").append(formatSingleValue(model, null))
        }
    }

    /**
     * Render an object's fields, each on its own line.
     * [firstLine] controls whether a leading newline is emitted before the
     * first field (omitted at top level).
     */
    private fun renderObjectFields(
        model: ObjectModel.Object,
        indent: Int,
        sb: StringBuilder,
        visitCounts: IdentityHashMap<ObjectModel.Object, Int>,
        firstLine: Boolean
    ) {
        if (model.fields.isEmpty()) {
            sb.append("{}")
            return
        }

        val count = visitCounts.getOrDefault(model, 0)
        if (count >= MAX_VISITS) {
            sb.append("{}")
            return
        }
        visitCounts[model] = count + 1

        model.fields.entries.forEachIndexed { index, (name, field) ->
            if (!firstLine || index > 0) sb.appendLine()
            sb.append(INDENT.repeat(indent)).append(name).append(":")
            renderFieldValue(field, indent, sb, visitCounts)
        }

        visitCounts[model] = count
    }

    /**
     * Render the value portion of a `name: <value>` entry.
     * Scalars go inline (`name: 0`); nested structures go on following lines.
     */
    private fun renderFieldValue(
        field: FieldModel,
        indent: Int,
        sb: StringBuilder,
        visitCounts: IdentityHashMap<ObjectModel.Object, Int>
    ) {
        when (val model = field.model) {
            is ObjectModel.Object -> {
                if (model.fields.isEmpty()) {
                    sb.append(" {}")
                } else {
                    val count = visitCounts.getOrDefault(model, 0)
                    if (count >= MAX_VISITS) {
                        sb.append(" {}")
                    } else {
                        sb.appendLine()
                        renderObjectFields(model, indent + 1, sb, visitCounts, firstLine = false)
                    }
                }
            }

            is ObjectModel.Array -> {
                sb.appendLine()
                renderArray(model, indent + 1, sb, visitCounts)
            }

            is ObjectModel.MapModel -> {
                sb.appendLine()
                renderMap(model, indent + 1, sb, visitCounts)
            }

            is ObjectModel.Single -> {
                sb.append(" ").append(formatSingleValue(model, field))
            }
        }
    }

    /**
     * Render an array as a single representative item with `- ` prefix.
     */
    private fun renderArray(
        model: ObjectModel.Array,
        indent: Int,
        sb: StringBuilder,
        visitCounts: IdentityHashMap<ObjectModel.Object, Int>
    ) {
        sb.append(INDENT.repeat(indent)).append("- ")
        when (val item = model.item) {
            is ObjectModel.Object -> {
                if (item.fields.isEmpty()) {
                    sb.append("{}")
                } else {
                    val count = visitCounts.getOrDefault(item, 0)
                    if (count >= MAX_VISITS) {
                        sb.append("{}")
                    } else {
                        visitCounts[item] = count + 1
                        // First field inline with `- `, rest on new lines
                        val entries = item.fields.entries.toList()
                        entries.forEachIndexed { index, (name, field) ->
                            if (index > 0) {
                                sb.appendLine()
                                sb.append(INDENT.repeat(indent + 1))
                            }
                            sb.append(name).append(":")
                            renderFieldValue(field, indent + 1, sb, visitCounts)
                        }
                        visitCounts[item] = count
                    }
                }
            }

            is ObjectModel.Array -> {
                renderArray(item, indent + 1, sb, visitCounts)
            }

            is ObjectModel.MapModel -> {
                sb.appendLine()
                renderMap(item, indent + 1, sb, visitCounts)
            }

            is ObjectModel.Single -> {
                sb.append(formatSingleValue(item, null))
            }
        }
    }

    /**
     * Render a map as `key: <default>` and `value: <default>` entries.
     */
    private fun renderMap(
        model: ObjectModel.MapModel,
        indent: Int,
        sb: StringBuilder,
        visitCounts: IdentityHashMap<ObjectModel.Object, Int>
    ) {
        sb.append(INDENT.repeat(indent)).append("key:")
        renderMapValue(model.keyType, indent, sb, visitCounts)
        sb.appendLine()
        sb.append(INDENT.repeat(indent)).append("value:")
        renderMapValue(model.valueType, indent, sb, visitCounts)
    }

    private fun renderMapValue(
        model: ObjectModel,
        indent: Int,
        sb: StringBuilder,
        visitCounts: IdentityHashMap<ObjectModel.Object, Int>
    ) {
        when (model) {
            is ObjectModel.Object -> {
                if (model.fields.isEmpty()) {
                    sb.append(" {}")
                } else {
                    sb.appendLine()
                    renderObjectFields(model, indent + 1, sb, visitCounts, firstLine = false)
                }
            }

            is ObjectModel.Array -> {
                sb.appendLine()
                renderArray(model, indent + 1, sb, visitCounts)
            }

            is ObjectModel.MapModel -> {
                sb.appendLine()
                renderMap(model, indent + 1, sb, visitCounts)
            }

            is ObjectModel.Single -> {
                sb.append(" ").append(formatSingleValue(model, null))
            }
        }
    }

    private fun formatSingleValue(model: ObjectModel.Single, fieldModel: FieldModel?): String {
        // Prefer field-level default value; otherwise use the type's default.
        // Note: do NOT stringify defaultValueForType's result here — we need
        // the original type (String vs Number vs Boolean) to format correctly.
        val defaultValue = fieldModel?.defaultValue ?: JsonType.defaultValueForType(model.type)
            ?: return "null"

        return when (defaultValue) {
            is String -> if (defaultValue.isEmpty()) "\"\"" else defaultValue
            is Number, is Boolean -> defaultValue.toString()
            else -> "null"
        }
    }
}
