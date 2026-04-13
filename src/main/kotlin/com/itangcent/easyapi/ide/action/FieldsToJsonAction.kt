package com.itangcent.easyapi.ide.action

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.psi.DefaultPsiClassHelper
import com.itangcent.easyapi.psi.JsonOption
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.JsonType

/**
 * Action to convert class fields to standard JSON format.
 *
 * Builds an object model from the class fields (including getters/setters)
 * and formats it as standard JSON with proper indentation and default values.
 *
 * @see FieldFormatAction for the base class
 */
class FieldsToJsonAction : FieldFormatAction("Fields To JSON") {
    override suspend fun format(project: Project, psiClass: PsiClass): String {
        val helper = DefaultPsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass, JsonOption.READ_GETTER_OR_SETTER, 10)
        return model?.let { formatJson(it) } ?: ""
    }
}

private fun formatJson(model: ObjectModel): String {
    val sb = StringBuilder()
    formatJson(model, 0, sb)
    return sb.toString()
}

private fun formatJson(model: ObjectModel, deep: Int, sb: StringBuilder) {
    when (model) {
        is ObjectModel.Single -> {
            val defaultValue = JsonType.defaultValueForType(model.type)
            when (defaultValue) {
                is String -> sb.append("\"$defaultValue\"")
                is Number, is Boolean -> sb.append(defaultValue)
                else -> sb.append("null")
            }
        }
        is ObjectModel.Object -> {
            if (model.fields.isEmpty()) {
                sb.append("{}")
                return
            }
            sb.append("{")
            sb.appendLine()
            val indent = "  ".repeat(deep + 1)
            model.fields.entries.forEachIndexed { index, (name, field) ->
                sb.append(indent)
                sb.append('"').append(name).append('"').append(": ")
                formatJson(field.model, deep + 1, sb)
                if (index < model.fields.size - 1) {
                    sb.append(",")
                }
                sb.appendLine()
            }
            sb.append("  ".repeat(deep))
            sb.append("}")
        }
        is ObjectModel.Array -> {
            sb.append("[")
            sb.appendLine()
            sb.append("  ".repeat(deep + 1))
            formatJson(model.item, deep + 1, sb)
            sb.appendLine()
            sb.append("  ".repeat(deep))
            sb.append("]")
        }
        is ObjectModel.MapModel -> {
            sb.append("{")
            sb.appendLine()
            val indent = "  ".repeat(deep + 1)
            sb.append(indent).append("\"key\": ")
            formatJson(model.keyType, deep + 1, sb)
            sb.append(",")
            sb.appendLine()
            sb.append(indent).append("\"value\": ")
            formatJson(model.valueType, deep + 1, sb)
            sb.appendLine()
            sb.append("  ".repeat(deep))
            sb.append("}")
        }
    }
}
