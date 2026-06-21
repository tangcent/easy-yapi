package com.itangcent.easyapi.exporter.formatter

import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.FieldOption
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.model.ObjectModelVisitTracker
import com.itangcent.easyapi.psi.type.JsonType
import com.itangcent.easyapi.util.text.append

/**
 * Formats an [ObjectModel] as a Java properties string.
 *
 * Converts the hierarchical object model into a flat properties format
 * with `key=value` pairs. Comments and enum options are included
 * as `#` comment lines.
 *
 * ## Output Format
 * ```properties
 * # User information
 * user.name=
 * user.age=0
 * # Status options:
 * # active :Active user
 * # inactive :Inactive user
 * user.status=
 * ```
 *
 * ## Recursive Reference Handling
 * To prevent infinite loops with self-referencing types, each object
 * is tracked and limited to [ObjectModel.DEFAULT_MAX_VISITS] expansions.
 *
 * ## Usage
 * ```kotlin
 * val formatter = PropertiesFormatter()
 * val properties = formatter.format(objectModel)
 * ```
 *
 * @see ObjectModel for the input model
 */
class PropertiesFormatter {

    fun format(model: ObjectModel, prefix: String = ""): String {
        val sb = StringBuilder()
        val tracker = ObjectModelVisitTracker()
        flatten(prefix, model, null, sb, tracker)
        return sb.toString()
    }

    private fun flatten(
        prefix: String,
        model: ObjectModel,
        fieldModel: FieldModel?,
        sb: StringBuilder,
        tracker: ObjectModelVisitTracker
    ) {
        when (model) {
            is ObjectModel.Object -> {
                if (!tracker.tryEnter(model)) {
                    // Recursive reference — emit as empty value to avoid infinite loop
                    sb.appendComment(fieldModel?.comment)
                    if (prefix.isNotEmpty()) {
                        sb.appendKV(prefix, "{}")
                    }
                    return
                }
                try {
                    sb.appendComment(fieldModel?.comment)
                    for ((name, field) in model.fields) {
                        val full = prefix.append(name, separator = ".")
                        flatten(full, field.model, field, sb, tracker)
                    }
                } finally {
                    tracker.exit(model)
                }
            }

            is ObjectModel.Array -> {
                sb.appendComment(fieldModel?.comment)
                sb.appendOptions(fieldModel?.options)
                sb.appendKV(prefix, "[]")
            }

            is ObjectModel.MapModel -> {
                sb.appendComment(fieldModel?.comment)
                sb.appendOptions(fieldModel?.options)
                sb.appendKV(prefix, "{}")
            }

            is ObjectModel.Single -> {
                if (prefix.isEmpty()) return
                sb.appendComment(fieldModel?.comment)
                sb.appendOptions(fieldModel?.options)
                val value = fieldModel?.defaultValue ?: JsonType.defaultValueForType(model.type)?.toString() ?: ""
                sb.appendKV(prefix, value)
            }
        }
    }

    private fun StringBuilder.appendComment(comment: String?) {
        if (comment.isNullOrBlank()) return
        for (line in comment.lines()) {
            if (line.isBlank()) continue
            if (isNotEmpty()) this.append('\n')
            this.append("# ").append(line)
        }
    }

    private fun StringBuilder.appendOptions(options: List<FieldOption>?) {
        if (options.isNullOrEmpty()) return
        for (option in options) {
            val desc = option.desc
            val value = option.value
            if (value != null || !desc.isNullOrBlank()) {
                if (isNotEmpty()) this.append('\n')
                this.append("# ")
                if (value != null) append(value)
                if (!desc.isNullOrBlank()) this.append(" :").append(desc)
            }
        }
    }

    private fun StringBuilder.appendKV(key: String, value: String) {
        if (isNotEmpty()) this.append('\n')
        this.append(key).append('=').append(value)
    }

}
