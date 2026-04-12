package com.itangcent.easyapi.rule.engine

import com.intellij.psi.PsiElement
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.rule.IntRuleMode
import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.rule.context.RuleContext
import com.itangcent.easyapi.rule.parser.*
import java.util.ServiceLoader

/**
 * Engine for evaluating configuration rules.
 *
 * ## Usage
 * ```kotlin
 * val engine = RuleEngine.getInstance(actionContext)
 *
 * // Typed evaluation via RuleKey
 * val name: String? = engine.evaluate(RuleKeys.API_NAME, psiMethod)
 * val ignore: Boolean = engine.evaluate(RuleKeys.FIELD_IGNORE, psiField)
 * engine.evaluate(RuleKeys.JSON_CLASS_PARSE_BEFORE, psiClass)
 * ```
 */
class RuleEngine(
    private val actionContext: ActionContext,
    private val configReader: ConfigReader,
    parsers: List<RuleParser> = emptyList()
) {
    private val parsers: List<RuleParser> = (parsers.ifEmpty { defaultParsers() }).also { list ->
        list.filterIsInstance<RuleEngineAware>().forEach { it.setRuleEngine(this) }
    }

    /**
     * Parse a single expression using the parser chain.
     * Exposed for parsers like [NegationParser] that need to delegate back to the engine.
     */
    suspend fun parseExpression(expression: String, context: RuleContext, ruleKey: RuleKey<*>? = null): Any? {
        return parse(expression, context, ruleKey)
    }

    // ════════════════════════════════════════════════════════════════
    //  Typed evaluate — the single entry point
    // ════════════════════════════════════════════════════════════════

    /** Evaluate a string rule. Returns null when no rule matches or all values are empty. */
    suspend fun evaluate(key: RuleKey.StringKey, element: PsiElement): String? {
        val ctx = RuleContext.from(actionContext, element)
        val values = ArrayList<String?>()
        forEachApplicable(key, ctx) { exp ->
            val v = runCatching { parse(exp, ctx, key) }
                .onFailure { e -> ctx.actionContext.console.warn("Rule evaluation failed for key=${key.name}", e) }
                .getOrNull()
                ?.toString()
            values.add(v)
        }
        return key.stringMode.aggregate(values)
    }

    /** Evaluate a string rule with context customization. */
    suspend fun evaluate(key: RuleKey.StringKey, element: PsiElement, contextHandle: (RuleContext) -> Unit): String? {
        val ctx = RuleContext.from(actionContext, element)
        contextHandle(ctx)
        val values = ArrayList<String?>()
        forEachApplicable(key, ctx) { exp ->
            val v = runCatching { parse(exp, ctx, key) }
                .onFailure { e -> ctx.actionContext.console.warn("Rule evaluation failed for key=${key.name}", e) }
                .getOrNull()
                ?.toString()
            values.add(v)
        }
        return key.stringMode.aggregate(values)
    }

    /** Evaluate a string rule without a PSI element (global/builtin rules). */
    suspend fun evaluate(key: RuleKey.StringKey): String? {
        val ctx = RuleContext.withoutElement(actionContext)
        val values = ArrayList<String?>()
        forEachApplicable(key, ctx) { exp ->
            val v = runCatching { parse(exp, ctx, key) }
                .onFailure { e -> ctx.console.warn("Rule evaluation failed for key=${key.name}", e) }
                .getOrNull()
                ?.toString()
            values.add(v)
        }
        return key.stringMode.aggregate(values)
    }

    /**
     * Evaluate a string rule for a PsiType with an optional context element.
     * Used for json.rule.convert rules where the type's canonical text is matched
     * by regex filters, and scripts can access the type via `it.type()`.
     */
    suspend fun evaluate(
        key: RuleKey.StringKey,
        psiType: com.intellij.psi.PsiType,
        contextElement: PsiElement? = null
    ): String? {
        val ctx = RuleContext.withPsiType(actionContext, psiType, contextElement)
        val values = ArrayList<String?>()
        forEachApplicable(key, ctx) { exp ->
            val v = runCatching { parse(exp, ctx, key) }
                .onFailure { e -> ctx.console.warn("Rule evaluation failed for key=${key.name}", e) }
                .getOrNull()
                ?.toString()
            values.add(v)
        }
        return key.stringMode.aggregate(values)
    }

    /** Evaluate a boolean rule. Returns false when no rule matches. */
    suspend fun evaluate(key: RuleKey.BooleanKey, element: PsiElement): Boolean {
        val ctx = RuleContext.from(actionContext, element)
        val values = ArrayList<Boolean?>()
        forEachApplicable(key, ctx) { exp ->
            val v = runCatching { parse(exp, ctx, key) }
                .onFailure { e -> ctx.console.warn("Rule evaluation failed for key=${key.name}", e) }
                .getOrNull()
                ?.let { toBoolean(it) }
            values.add(v)
        }
        return key.booleanMode.aggregate(values)
    }

    /** Evaluate an int rule. Returns null when no rule matches. */
    suspend fun evaluate(key: RuleKey.IntKey, element: PsiElement): Int? {
        val ctx = RuleContext.from(actionContext, element)
        val values = ArrayList<Int?>()
        forEachApplicable(key, ctx) { exp ->
            val v = runCatching { parse(exp, ctx, key) }
                .onFailure { e -> ctx.console.warn("Rule evaluation failed for key=${key.name}", e) }
                .getOrNull()
                ?.let { toInt(it) }
            values.add(v)
        }
        return IntRuleMode.aggregate(values)
    }

    /** Fire an event rule (side-effect only). */
    suspend fun evaluate(key: RuleKey.EventKey, element: PsiElement) {
        val ctx = RuleContext.from(actionContext, element)
        fireEvent(key, ctx)
    }

    /** Fire an event rule with context customization. */
    suspend fun evaluate(key: RuleKey.EventKey, element: PsiElement, contextHandle: (RuleContext) -> Unit) {
        val ctx = RuleContext.from(actionContext, element)
        contextHandle(ctx)
        fireEvent(key, ctx)
    }

    /** Fire an event rule without a PSI element (global events). */
    suspend fun evaluate(key: RuleKey.EventKey, contextHandle: (RuleContext) -> Unit = {}) {
        val ctx = RuleContext.withoutElement(actionContext)
        contextHandle(ctx)
        fireEvent(key, ctx)
    }

    // ════════════════════════════════════════════════════════════════
    //  Internal
    // ════════════════════════════════════════════════════════════════

    private suspend fun fireEvent(key: RuleKey.EventKey, ctx: RuleContext) {
        forEachApplicable(key, ctx) { exp ->
            try {
                parse(exp, ctx, key)
            } catch (e: Exception) {
                ctx.console.warn("Rule event failed for key=${key.name}", e)
                if (key.eventMode.throwOnError) throw e
            }
        }
    }

    private suspend fun forEachApplicable(
        key: RuleKey<*>,
        ctx: RuleContext,
        action: suspend (String) -> Unit
    ) {
        for ((exp, filterExp) in loadExpressionsWithFilters(key)) {
            ctx.regexGroups = null
            val shouldApply = if (filterExp != null) {
                runCatching { parse(filterExp, ctx, FILTER_KEY) }
                    .onFailure { e -> ctx.console.warn("Filter evaluation failed for key=${key.name}", e) }
                    .getOrNull()
                    ?.let { toBoolean(it) }
                    ?: false
            } else {
                true
            }
            if (shouldApply) {
                action(exp)
            }
            ctx.regexGroups = null
        }
    }

    private suspend fun parse(expression: String, ctx: RuleContext, ruleKey: RuleKey<*>? = null): Any? {
        val parser = parsers.firstOrNull { it.canParse(expression) }
        return parser?.parse(expression, ctx, ruleKey)
    }

    private fun loadExpressionsWithFilters(key: RuleKey<*>): List<Pair<String, String?>> {
        val result = ArrayList<Pair<String, String?>>()

        for (k in key.allNames) {
            val rules = configReader.getAll(k)
            rules.forEach { exp ->
                result.add(exp to null)
            }

            val indexedKeyPrefix = "$k["
            configReader.foreach(
                { cfgKey -> cfgKey.startsWith(indexedKeyPrefix) },
                { indexedKey, value ->
                    val filterExp = indexedKey.removePrefix(k).removeSurrounding("[", "]")
                    result.add(value to filterExp)
                }
            )
        }

        return result
    }

    private fun defaultParsers(): List<RuleParser> {
        val loaded = runCatching { ServiceLoader.load(RuleParser::class.java).toList() }.getOrNull().orEmpty()
        return (loaded + listOf(
            NegationParser(),
            GroovyScriptParser(),
            RegexParser(),
            AnnotationExpressionParser(),
            TagExpressionParser(),
            ClassMatchParser(),
            TypeMatchParser(),
            LiteralParser()
        )).distinctBy { it::class.java.name }
    }

    private fun toBoolean(value: Any): Boolean = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        else -> {
            val s = value.toString().trim()
            s.equals("true", true) || s == "1" || s.equals("yes", true) || s.equals("y", true)
        }
    }

    private fun toInt(value: Any): Int? = when (value) {
        is Int -> value
        is Number -> value.toInt()
        else -> value.toString().trim().toIntOrNull()
    }

    companion object {
        /** Synthetic key used for filter expression evaluation. */
        private val FILTER_KEY = RuleKey.boolean("__filter__")

        fun getInstance(actionContext: ActionContext): RuleEngine =
            actionContext.instance(RuleEngine::class)
    }
}
