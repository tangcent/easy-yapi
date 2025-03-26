package com.itangcent.idea.plugin.api.export.markdown

import com.itangcent.common.constant.Attrs
import com.itangcent.common.kit.KVUtils
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.Request
import com.itangcent.common.utils.*
import com.itangcent.http.RequestUtils
import com.itangcent.idea.plugin.api.export.markdown.ObjectWriterBuilder.AbstractObjectWriter
import com.itangcent.intellij.extend.lazyBean
import com.itangcent.intellij.extend.toPrettyString
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.util.forEachValid
import javax.script.ScriptContext
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import javax.script.SimpleScriptContext

/**
 * Context for template evaluation
 */
class TemplateContext(val request: Request) : SimpleExtensible() {
    override fun setExt(attr: String, value: Any?) {
        if (attr.contains(".")) {
            val parts = attr.split(".")
            val rootKey = parts[0]

            // Get existing root map or create a new one
            @Suppress("UNCHECKED_CAST")
            var current = super.getExt<MutableMap<String, Any?>>(rootKey)

            // If the root map is newly created, set it in parent
            if (current == null) {
                current = HashMap<String, Any?>()
                super.setExt(rootKey, current)
            }

            // Navigate to the parent of the leaf node
            var currentMap: MutableMap<String, Any?> = current
            for (i in 1 until parts.size - 1) {
                val part = parts[i]

                // Get existing map at this level or create a new one
                @Suppress("UNCHECKED_CAST")
                var nextMap = currentMap[part] as? MutableMap<String, Any?>
                if (nextMap == null) {
                    nextMap = HashMap<String, Any?>()
                    currentMap[part] = nextMap
                }
                currentMap = nextMap
            }

            // Set the value at the final level
            currentMap[parts.last()] = value
            return
        }
        super.setExt(attr, value)
    }

    override fun <T> getExt(attr: String): T? {
        if (attr.contains(".")) {
            val parts = attr.split(".")
            var current: Any? = super.getExt<Any>(parts[0])

            // Navigate through the parts to find the nested value
            for (i in 1 until parts.size) {
                if (current == null) return null

                current = when (current) {
                    is Map<*, *> -> (current as Map<*, *>)[parts[i]]
                    else -> return null // If not a map, can't navigate deeper
                }
            }

            @Suppress("UNCHECKED_CAST")
            return current as T?
        }
        return super.getExt(attr)
    }
}

/**
 * Base interface for all template expressions
 */
interface TemplateExpression {
    fun eval(context: TemplateContext, stringBuilder: StringBuilder)

    companion object {

        internal fun parseTemplate(template: String): TemplateExpression {
            var lastIndex = 0

            // Match all patterns: ${...}, $xx, $xx.xx, ${if ...}, ${end}, and ${@xx=yy}
            val regex = Regex("\\$\\{@([^=]+)=([^}]+)}|\\$\\{([^}]+)}|\\$([a-zA-Z][a-zA-Z0-9_.]*)")

            val expressions = mutableListOf<TemplateExpression>()
            var conditionExpressionBuilder: ConditionalExpressionBuilder? = null

            fun addExpression(expression: TemplateExpression) {
                if (conditionExpressionBuilder != null) {
                    conditionExpressionBuilder?.then(expression)
                } else {
                    expressions.add(expression)
                }
            }

            regex.findAll(template).forEach {
                if (it.range.first > lastIndex) {
                    addExpression(PlainText(template.substring(lastIndex, it.range.first)))
                }

                // Check which pattern matched
                val variableMatch = it.groupValues[1]
                val valueMatch = it.groupValues[2]
                val scriptMatch = it.groupValues[3]
                val simpleMatch = it.groupValues[4]

                when {
                    variableMatch.isNotEmpty() && valueMatch.isNotEmpty() -> {
                        // ${@xx=yy} pattern
                        addExpression(ContextVariableSet(variableMatch, valueMatch))
                    }

                    scriptMatch.startsWith("if ") -> {
                        // ${if ...} pattern
                        val condition = scriptMatch.removePrefix("if").trim()
                        conditionExpressionBuilder = ConditionalExpressionBuilder(parseCondition(condition))
                    }

                    scriptMatch == "end" -> {
                        // ${end} pattern
                        if (conditionExpressionBuilder == null) {
                            throw IllegalStateException("Unexpected \${end} found")
                        }
                        expressions.add(conditionExpressionBuilder.build())
                        conditionExpressionBuilder = null
                    }

                    scriptMatch.isNotEmpty() -> {
                        // ${...} pattern
                        addExpression(ScriptTemplateExpression(scriptMatch))
                    }

                    simpleMatch.isNotEmpty() -> {
                        // $xx or $xx.xx pattern
                        if (simpleMatch.contains(".")) {
                            val parts = simpleMatch.split(".")
                            if (parts.size == 2 && parts[0] == "response") {
                                addExpression(ResponseProperty(parts[1]))
                            } else {
                                addExpression(ScriptTemplateExpression(simpleMatch))
                            }
                        } else {
                            addExpression(RequestProperty(simpleMatch))
                        }
                    }
                }

                lastIndex = it.range.last + 1
            }

            if (lastIndex < template.length) {
                addExpression(PlainText(template.substring(lastIndex)))
            }

            if (conditionExpressionBuilder != null) {
                expressions.add(conditionExpressionBuilder.build())
            }

            return CompositeTemplateExpression(expressions)
        }

        internal fun parseCondition(condition: String): Condition {
            // Handle simple property check (e.g., "form", "header")
            if (Regex("^[a-zA-Z_][a-zA-Z0-9_]*$").matches(condition)) {
                return SinglePropertyCondition(condition)
            }

            // Default to script condition for any other complex expressions
            return ScriptCondition(condition)
        }
    }
}

fun TemplateExpression.eval(request: Request, stringBuilder: StringBuilder) {
    eval(TemplateContext(request), stringBuilder)
}

/**
 * Expression that sets a context variable
 */
class ContextVariableSet(
    private val name: String,
    private val value: String
) : TemplateExpression {
    override fun eval(context: TemplateContext, stringBuilder: StringBuilder) {
        context.setExt(name, value)
    }
}

/**
 * Represents plain text in the template
 */
class PlainText(
    private val text: String
) : TemplateExpression {
    override fun eval(context: TemplateContext, stringBuilder: StringBuilder) {
        stringBuilder.append(text)
    }
}

fun TemplateContext.asText(value: Any?): String {
    val type = when (value) {
        null -> "null"
        is String -> "string"
        is Int -> "int"
        is Long -> "long"
        is Float -> "float"
        is Double -> "double"
        is Boolean -> "boolean"
        else -> "object"
    }
    return getExt<String>("text.$type.$value") ?: value?.toPrettyString() ?: ""
}

/**
 * Represents a request property access in the template
 */
class RequestProperty(
    private val property: String
) : TemplateExpression {
    override fun eval(context: TemplateContext, stringBuilder: StringBuilder) {
        val value = context.request.getPropertyValue(property)
        value?.let { stringBuilder.append(it) }
    }
}

/**
 * Represents a response property access in the template
 */
class ResponseProperty(
    private val property: String
) : TemplateExpression {
    override fun eval(context: TemplateContext, stringBuilder: StringBuilder) {
        val value = context.request.response?.firstOrNull()?.getPropertyValue(property)
        value?.let { stringBuilder.append(it) }
    }
}

/**
 * Represents a JSON expression in the template
 */
class MDJsonExpression(
    private val value: Any?
) : TemplateExpression {
    override fun eval(context: TemplateContext, stringBuilder: StringBuilder) {
        if (value == null) {
            return
        }
        val copyBody = value.copy() ?: return
        KVUtils.useAttrAsValue(
            copyBody,
            Attrs.DEMO_ATTR, Attrs.DEFAULT_VALUE_ATTR
        )
        stringBuilder.append(RequestUtils.parseRawBody(copyBody))
    }
}

/**
 * Represents a table expression in the template
 */
class MDTableExpression(
    private val rows: List<Any>,
    private val titles: List<Pair<String, String>>
) : TemplateExpression {
    override fun eval(context: TemplateContext, stringBuilder: StringBuilder) {
        //append header
        for ((_, title) in titles) {
            stringBuilder.append("| ${MarkdownEscapeUtils.escape(title)} ")
        }
        stringBuilder.append("|\n")

        // append separator
        for ((_, _) in titles) {
            stringBuilder.append("|------")
        }
        stringBuilder.append("|\n")

        // append rows
        for (row in rows) {
            for ((property, _) in titles) {
                val value = row.getPropertyValue(property)
                stringBuilder.append("| ${MarkdownEscapeUtils.escape(context.asText(value))} ")
            }
            stringBuilder.append("|\n")
        }
    }
}

/**
 * Represents a composite expression that combines multiple template expressions
 */
class CompositeTemplateExpression(
    private val expressions: List<TemplateExpression>
) : TemplateExpression {
    override fun eval(context: TemplateContext, stringBuilder: StringBuilder) {
        expressions.forEach { it.eval(context, stringBuilder) }
    }
}

/**
 * Helper class for managing script engine initialization and context setup
 */
private class ScriptEngineHelper {
    private val engine: ScriptEngine by lazy { ScriptEngineManager().getEngineByName("groovy") }

    private val logger by lazyBean<Logger>()

    fun eval(script: String, context: TemplateContext): Any? {
        val simpleScriptContext = SimpleScriptContext()

        // Setting script context attributes based on the provided context.
        context.exts()?.forEach {
            simpleScriptContext.setAttribute(
                it.key,
                it.value,
                ScriptContext.ENGINE_SCOPE
            )
        }

        // Bind the request object
        simpleScriptContext.setAttribute(
            "api", context.request,
            ScriptContext.ENGINE_SCOPE
        )
        simpleScriptContext.setAttribute(
            "response", context.request.response?.first(),
            ScriptContext.ENGINE_SCOPE
        )

        // Bind the context
        simpleScriptContext.setAttribute(
            "context", context,
            ScriptContext.ENGINE_SCOPE
        )

        // Bind all request properties directly to the context
        fun getAllFields(clazz: Class<*>): List<java.lang.reflect.Field> {
            val fields = mutableListOf<java.lang.reflect.Field>()
            var currentClass: Class<*>? = clazz
            while (currentClass != null) {
                fields.addAll(currentClass.declaredFields)
                currentClass = currentClass.superclass
            }
            return fields
        }

        getAllFields(context.request.javaClass).forEach { field ->
            if (simpleScriptContext.getAttribute(field.name) != null) {
                return@forEach
            }
            field.isAccessible = true
            val value = field.get(context.request)
            simpleScriptContext.setAttribute(
                field.name, value,
                ScriptContext.ENGINE_SCOPE
            )
        }

        // Bind the md helper
        simpleScriptContext.setAttribute(
            "md", MD,
            ScriptContext.ENGINE_SCOPE
        )

        return try {
            engine.eval(script, simpleScriptContext)
        } catch (e: Exception) {
            logger.traceError("failed eval script: $script", e)
            null
        }
    }
}

/**
 * Represents a script expression in the template
 */
class ScriptTemplateExpression(
    private val script: String
) : TemplateExpression {

    private val scriptHelper = ScriptEngineHelper()

    override fun eval(context: TemplateContext, stringBuilder: StringBuilder) {
        val result = scriptHelper.eval(script, context) ?: script
        when (result) {
            is String -> stringBuilder.append(result)
            is TemplateExpression -> result.eval(context, stringBuilder)
            else -> stringBuilder.append(result.toPrettyString())
        }
    }
}

/**
 * Represents an object expression in the template
 */
class MDObjectExpression(
    private val value: Any?,
    private val titles: Map<String, String>
) : TemplateExpression {

    override fun eval(context: TemplateContext, stringBuilder: StringBuilder) {
        if (value == null) {
            return
        }

        val writer: Writer = { stringBuilder.append(it) }
        val customObjectWriter = CustomObjectWriter(writer, titles.map { it.key to it.value }, context)
        customObjectWriter.writeHeader()
        customObjectWriter.writeObject(value, "")
    }
}

/**
 * Custom object writer for markdown formatting
 */
class CustomObjectWriter(
    writer: Writer,
    private val titles: List<Pair<String, String>>,
    private val context: TemplateContext
) : AbstractObjectWriter(writer) {
    override val tableWriter: TableWriter = TableWriterImpl(writer)

    private inner class TableWriterImpl(
        private val writer: Writer,
    ) : TableWriter {
        override fun writeHeaders() {
            //append header
            for ((_, title) in titles) {
                writer("| $title ")
            }
            writer("|\n")

            // append separator
            for ((_, _) in titles) {
                writer("|------")
            }
            writer("|\n")
        }

        override fun addRow(columns: Collection<*>) {
            columns.forEach { writer("| ${format(it)} ") }
            writer("|\n")
        }

        override fun <T> addRows(rows: List<T>?, vararg columns: (T) -> Any?) {
            rows?.forEach { row ->
                addRow(columns.map { it(row) })
            }
        }

        private fun format(any: Any?, escape: Boolean = true): String {
            return when {
                any == null -> {
                    ""
                }

                any is Boolean -> {
                    context.asText(any)
                }

                escape -> {
                    MarkdownEscapeUtils.escape(context.asText(any))
                }

                else -> {
                    context.asText(any)
                }
            }
        }
    }

    override fun writeBody(obj: Any?, name: String, desc: String) {
        writeBody(obj, name, null, null, desc, 0)
    }

    @Suppress("UNCHECKED_CAST")
    fun writeBody(obj: Any?, name: String, required: Boolean?, default: String?, desc: String, deep: Int) {
        var type: String? = null
        when (obj) {
            null -> type = "object"
            is String -> type = "string"
            is Number -> type = if (obj is Int || obj is Long) "integer" else "number"
            is Boolean -> type = "boolean"
        }
        if (type != null) {
            addBodyProperty(deep, name, type, required, default, desc)
            return
        }

        if (obj is Array<*>) {
            addBodyProperty(deep, name, "array", required, default, desc)
            if (obj.isNotEmpty()) {
                obj.forEach {
                    writeBody(it, "", null, null, "", deep + 1)
                }
            } else {
                writeBody(null, "", null, null, "", deep + 1)
            }
        } else if (obj is Collection<*>) {
            addBodyProperty(deep, name, "array", required, default, desc)
            if (obj.isNotEmpty()) {
                obj.forEach {
                    writeBody(it, "", null, null, "", deep + 1)
                }
            } else {
                writeBody(null, "", null, null, "", deep + 1)
            }
        } else if (obj is Map<*, *>) {
            if (deep > 0) {
                addBodyProperty(deep, name, "object", required, default, desc)
            }
            val comments: HashMap<String, Any?>? = obj[Attrs.COMMENT_ATTR] as? HashMap<String, Any?>?
            val requireds: HashMap<String, Any?>? = obj[Attrs.REQUIRED_ATTR] as? HashMap<String, Any?>?
            val defaults: HashMap<String, Any?>? = obj[Attrs.DEFAULT_VALUE_ATTR] as? HashMap<String, Any?>?
            obj.forEachValid { k, v ->
                val key = k.toString()
                val propertyDesc: String? = KVUtils.getUltimateComment(comments, k)
                writeBody(
                    v, key,
                    requireds?.get(key) as? Boolean,
                    defaults?.get(key) as? String,
                    propertyDesc ?: "",
                    deep + 1
                )
            }
        } else {
            addBodyProperty(deep, name, "object", required, default, desc)
        }
    }

    fun addBodyProperty(
        deep: Int,
        name: String,
        type: String?,
        required: Boolean?,
        default: Any?,
        desc: String
    ) {
        val map = mapOf<String, Any?>(
            "name" to name,
            "type" to type,
            "required" to required,
            "default" to default,
            "desc" to desc
        )
        super.addBodyProperty(deep, *titles.mapToTypedArray { map[it.first] })
    }
}

/**
 * Helper object for markdown formatting
 */
object MD {
    fun table(rows: List<Any>?): MDTableBuilder {
        return MDTableBuilder(rows ?: emptyList())
    }

    fun json(value: Any?): MDJsonExpression {
        return MDJsonExpression(value)
    }

    fun json5(value: Any?): MDJson5Expression {
        return MDJson5Expression(value)
    }

    fun objectTable(value: Any?): MDObjectTableBuilder {
        return MDObjectTableBuilder(value)
    }
}

/**
 * Builder for table expressions
 */
class MDTableBuilder(
    private val rows: List<Any>
) {
    fun title(titles: Map<String, String>): MDTableExpression {
        return MDTableExpression(rows, titles.map {
            it.key to it.value
        })
    }
}

/**
 * Builder for object table expressions
 */
class MDObjectTableBuilder(
    private val value: Any?
) {
    fun title(titles: Map<String, String>): MDObjectExpression {
        return MDObjectExpression(value, titles)
    }
}

/**
 * Interface for conditions in template expressions
 */
interface Condition {
    fun eval(context: TemplateContext): Boolean

    // For backward compatibility
    fun eval(request: Request): Boolean {
        return eval(TemplateContext(request))
    }
}

/**
 * Condition that checks a single property
 */
class SinglePropertyCondition(
    private val property: String
) : Condition {
    override fun eval(context: TemplateContext): Boolean {
        val value = context.request.getPropertyValue(property)
        return value.checkCondition()
    }
}

/**
 * Condition that evaluates a script
 */
class ScriptCondition(
    private val script: String
) : Condition {
    private val scriptHelper = ScriptEngineHelper()

    override fun eval(context: TemplateContext): Boolean {
        val result = scriptHelper.eval(script, context) ?: false
        return result.checkCondition()
    }
}

/**
 * Expression that evaluates conditionally
 */
class ConditionalExpression(
    private val condition: Condition,
    private val expression: TemplateExpression
) : TemplateExpression {
    override fun eval(context: TemplateContext, stringBuilder: StringBuilder) {
        if (condition.eval(context)) {
            expression.eval(context, stringBuilder)
        }
    }
}

/**
 * Builder for conditional expressions
 */
class ConditionalExpressionBuilder(
    private val condition: Condition
) {
    private var expressions = ArrayList<TemplateExpression>()

    fun then(expression: TemplateExpression): ConditionalExpressionBuilder {
        expressions.add(expression)
        return this
    }

    fun build(): ConditionalExpression {
        return ConditionalExpression(condition, CompositeTemplateExpression(expressions))
    }
}

private fun Any?.checkCondition(): Boolean {
    if (this.anyIsNullOrEmpty()) {
        return false
    }
    if (this is Number) {
        return this.toDouble() != 0.0
    }
    return this != null && this != false && this != ""
}

/**
 * Represents a JSON5 expression in the template
 */
class MDJson5Expression(
    private val value: Any?
) : TemplateExpression {

    val json5Formatter by lazyBean<com.itangcent.idea.plugin.format.Json5Formatter>()

    override fun eval(context: TemplateContext, stringBuilder: StringBuilder) {
        if (value == null) {
            return
        }

        val copyBody = value.copy() ?: return
        KVUtils.useAttrAsValue(
            copyBody,
            Attrs.DEMO_ATTR, Attrs.DEFAULT_VALUE_ATTR
        )

        stringBuilder.append(json5Formatter.format(copyBody))
    }
}