package com.itangcent.easyapi.rule.engine

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.itangcent.easyapi.rule.IntRuleMode
import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.rule.RuleProvider
import com.itangcent.easyapi.rule.context.RuleContext
import com.itangcent.easyapi.rule.parser.*

/**
 * Engine for evaluating configuration rules.
 *
 * ## Usage
 * ```kotlin
 * val engine = RuleEngine.getInstance(project)
 *
 * // Typed evaluation via RuleKey
 * val name: String? = engine.evaluate(RuleKeys.API_NAME, psiMethod)
 * val ignore: Boolean = engine.evaluate(RuleKeys.FIELD_IGNORE, psiField)
 * engine.evaluate(RuleKeys.JSON_CLASS_PARSE_BEFORE, psiClass)
 * ```
 */
@Service(Service.Level.PROJECT)
class RuleEngine internal constructor(
    private val project: Project
) {
    private val ruleProvider: RuleProvider
        get() = RuleProvider.getInstance(project)

    private val parsers: List<RuleParser> = defaultParsers().also { list ->
        list.filterIsInstance<RuleEngineAware>().forEach { it.setRuleEngine(this) }
    }

    private fun defaultParsers(): List<RuleParser> {
        return listOf(
            NegationParser(),
            GroovyScriptParser(),
            RegexParser(),
            AnnotationExpressionParser(),
            TagExpressionParser(),
            ClassMatchParser(),
            TypeMatchParser(),
            LiteralParser()
        )
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
    suspend fun evaluate(key: RuleKey.StringKey, element: PsiElement, fieldContext: String? = null): String? {
        val ctx = RuleContext.from(project, element, fieldContext)
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

    suspend fun evaluate(key: RuleKey.StringKey, element: PsiElement, contextHandle: (RuleContext) -> Unit): String? {
        val ctx = RuleContext.from(project, element)
        contextHandle(ctx)
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

    /** Evaluate a string rule without a PSI element (global/builtin rules). */
    suspend fun evaluate(key: RuleKey.StringKey): String? {
        val ctx = RuleContext.withoutElement(project)
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
        val ctx = RuleContext.withPsiType(project, psiType, contextElement)
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
    suspend fun evaluate(key: RuleKey.BooleanKey, element: PsiElement, fieldContext: String? = null): Boolean {
        val ctx = RuleContext.from(project, element, fieldContext)
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

    /** Evaluate a boolean rule with context customization. */
    suspend fun evaluate(key: RuleKey.BooleanKey, element: PsiElement, contextHandle: (RuleContext) -> Unit): Boolean {
        val ctx = RuleContext.from(project, element)
        contextHandle(ctx)
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
        val ctx = RuleContext.from(project, element)
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
    suspend fun evaluate(key: RuleKey.EventKey, element: PsiElement, fieldContext: String? = null) {
        val ctx = RuleContext.from(project, element, fieldContext)
        fireEvent(key, ctx)
    }

    /** Fire an event rule with context customization. */
    suspend fun evaluate(key: RuleKey.EventKey, element: PsiElement, contextHandle: (RuleContext) -> Unit) {
        val ctx = RuleContext.from(project, element)
        contextHandle(ctx)
        fireEvent(key, ctx)
    }

    /** Fire an event rule without a PSI element (global events). */
    suspend fun evaluate(key: RuleKey.EventKey, contextHandle: (RuleContext) -> Unit = {}) {
        val ctx = RuleContext.withoutElement(project)
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
        for ((expression, filter) in ruleProvider.getRules(key)) {
            ctx.regexGroups = null
            val shouldApply = if (filter != null) {
                runCatching { parse(filter, ctx, FILTER_KEY) }
                    .onFailure { e -> ctx.console.warn("Filter evaluation failed for key=${key.name}", e) }
                    .getOrNull()
                    ?.let { toBoolean(it) }
                    ?: false
            } else {
                true
            }
            if (shouldApply) {
                action(expression)
            }
            ctx.regexGroups = null
        }
    }

    private suspend fun parse(expression: String, ctx: RuleContext, ruleKey: RuleKey<*>? = null): Any? {
        val parser = parsers.firstOrNull { it.canParse(expression) }
        return parser?.parse(expression, ctx, ruleKey)
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

        fun getInstance(project: Project): RuleEngine = project.service()
    }
}
