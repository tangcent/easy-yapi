package com.itangcent.idea.plugin.rule

import com.google.inject.Inject
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.itangcent.idea.psi.ParseScriptContext
import com.itangcent.intellij.config.rule.*
import com.itangcent.intellij.jvm.element.ExplicitField
import com.itangcent.intellij.jvm.element.ExplicitMethod

class FieldPatternRuleParser : SimpleRuleParser() {

    @Inject
    private lateinit var simpleRuleParser: SimpleRuleParser
    override fun contextOf(target: Any, context: PsiElement?): RuleContext {
        return simpleRuleParser.contextOf(target, context)
    }

    override fun parseBooleanRule(rule: String): BooleanRule {
        return if (rule.contains("|")) {
            val pathStr = rule.substringBefore("|").trim()
            val typeStr = rule.substringAfter("|", "*").trim()
            FieldPatternRule(
                FieldPathMatcher.parseRegexOrConstant(pathStr.trim()),
                getTypePredict(typeStr)
            )
        } else {
            FieldPatternRule(FieldPathMatcher.parseRegexOrConstant(rule.trim()), alwaysTrue)
        }
    }

    override fun parseEventRule(rule: String): EventRule? {
        throw NotImplementedError("Not yet supported")
    }

    override fun parseStringRule(rule: String): StringRule? {
        throw NotImplementedError("Not yet supported")
    }
}

class FieldPatternRule(
    val pathPredict: (String?) -> Boolean,
    val typePredict: (String?) -> Boolean,
) : BooleanRule {
    override fun invoke(context: RuleContext): Boolean {
        val path = context.getExt<ParseScriptContext>("fieldContext")?.path() ?: return false
        val name = context.rawType() ?: return false
        return pathPredict(path) && typePredict(name)
    }

    private fun RuleContext.rawType(): String? {
        when (val core = getCore()) {
            is ExplicitField -> {
                return core.getType().canonicalText()
            }

            is ExplicitMethod -> {
                return core.getReturnType()?.canonicalText()
            }

            is PsiField -> {
                return core.type.canonicalText
            }

            is PsiMethod -> {
                return core.returnType?.canonicalText
            }

            else -> return null
        }
    }
}

private val jsTypes = mapOf(
    "bool" to arrayOf("Bool", "boolean"),
    "boolean" to arrayOf("Boolean", "java.lang.Boolean", "kotlin.Boolean"),
    "byte" to arrayOf("Byte", "java.lang.Byte", "kotlin.Byte"),
    "number" to arrayOf("Number", "java.lang.Number", "short", "int", "long", "float", "double"),
    "short" to arrayOf("Short", "java.lang.Short", "kotlin.Short"),
    "integer" to arrayOf("int"),
    "int" to arrayOf("Int", "Integer", "java.lang.Integer", "kotlin.Int"),
    "long" to arrayOf("Long", "java.lang.Long", "kotlin.Long"),
    "float" to arrayOf("Float", "java.lang.Float", "kotlin.Float"),
    "double" to arrayOf("Double", "java.lang.Double", "kotlin.Double"),
    "string" to arrayOf("String", "java.lang.String", "kotlin.String"),
    "date" to arrayOf("Date", "java.util.Date", "java.sql.Date", "java.time.LocalDate"),
    "datetime" to arrayOf("Date", "java.util.Date", "java.sql.Date", "java.time.LocalDateTime")
)

private typealias TypePredict = (String?) -> Boolean

private val alwaysTrue: TypePredict = { true }

private val typePredictCache = mutableMapOf<String, TypePredict>()

private fun getTypePredict(type: String): TypePredict {
    typePredictCache[type]?.let { return it }
    synchronized(typePredictCache) {
        if (type.isEmpty() || type == "*") {
            return alwaysTrue
        }
        val alias = collectTypeAlias(type)
        val typePredict: TypePredict = {
            alias.contains(it)
        }
        typePredictCache[type] = typePredict
        return typePredict
    }
}

private fun collectTypeAlias(type: String): List<String> {
    val alias = mutableListOf<String>()
    collectTypeAlias(type) {
        alias.add(it)
    }
    return alias
}

private fun collectTypeAlias(type: String, handle: (String) -> Unit) {
    handle(type)
    jsTypes[type]?.forEach {
        collectTypeAlias(it, handle)
    }
}
