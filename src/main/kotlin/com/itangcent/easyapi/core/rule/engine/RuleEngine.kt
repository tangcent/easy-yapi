package com.itangcent.easyapi.core.rule.engine

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.itangcent.easyapi.core.rule.RuleKey
import com.itangcent.easyapi.core.rule.RuleProvider
import com.itangcent.easyapi.core.rule.RuleResult
import com.itangcent.easyapi.core.rule.context.RuleContext
import com.itangcent.easyapi.core.rule.parser.*
import com.itangcent.easyapi.core.util.asBooleanOrNull
import com.itangcent.easyapi.core.util.asInt
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.cancellation.CancellationException

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

    suspend fun parseExpression(expression: String, context: RuleContext, ruleKey: RuleKey<*>? = null): Any? {
        return parse(expression, context, ruleKey)
    }

    suspend fun evaluate(key: RuleKey.StringKey, element: PsiElement, fieldContext: String? = null): String? {
        return forEachApplicable(key) { RuleContext.from(project, element, fieldContext) }
    }

    suspend fun evaluate(
        key: RuleKey.StringKey,
        element: PsiElement,
        containingClass: com.intellij.psi.PsiClass,
        fieldContext: String? = null
    ): String? {
        return forEachApplicable(key) {
            RuleContext.fromMember(project, element, containingClass, fieldContext)
        }
    }

    suspend fun evaluate(key: RuleKey.StringKey, element: PsiElement, contextHandle: (RuleContext) -> Unit): String? {
        return forEachApplicable(key) {
            RuleContext.from(project, element).also(contextHandle)
        }
    }

    suspend fun evaluate(
        key: RuleKey.StringKey,
        element: PsiElement,
        containingClass: com.intellij.psi.PsiClass,
        contextHandle: (RuleContext) -> Unit
    ): String? {
        return forEachApplicable(key) {
            RuleContext.fromMember(project, element, containingClass).also(contextHandle)
        }
    }

    suspend fun evaluate(key: RuleKey.StringKey): String? {
        return forEachApplicable(key) { RuleContext.withoutElement(project) }
    }

    suspend fun evaluate(
        key: RuleKey.StringKey,
        psiType: com.intellij.psi.PsiType,
        contextElement: PsiElement? = null
    ): String? {
        return forEachApplicable(key) { RuleContext.withPsiType(project, psiType, contextElement) }
    }

    /**
     * Evaluates a string rule against a resolved type.
     * Uses the resolved type's [ResolvedType.qualifiedName] as the typeText for regex matching,
     * ensuring rules like `#regex:Mono<(.*?)>` match the fully-resolved type text.
     */
    suspend fun evaluate(
        key: RuleKey.StringKey,
        resolvedType: com.itangcent.easyapi.core.psi.type.ResolvedType,
        contextElement: PsiElement? = null
    ): String? {
        return forEachApplicable(key) {
            RuleContext.withResolvedType(
                project,
                resolvedType,
                contextElement = contextElement
            )
        }
    }

    suspend fun evaluate(key: RuleKey.BooleanKey, element: PsiElement, fieldContext: String? = null): Boolean {
        return forEachApplicable(key) { RuleContext.from(project, element, fieldContext) } ?: false
    }

    /**
     * Evaluates a boolean rule and returns `null` when no rule is configured
     * for the key, letting the caller apply a framework-specific default.
     */
    suspend fun evaluateOrNull(
        key: RuleKey.BooleanKey,
        element: PsiElement,
        fieldContext: String? = null
    ): Boolean? {
        return forEachApplicable(key) { RuleContext.from(project, element, fieldContext) }
    }

    /**
     * Evaluates a member rule from the perspective of [containingClass].
     * This preserves the distinction between a member's current containing
     * class and its original declaring class for inherited members.
     */
    suspend fun evaluate(
        key: RuleKey.BooleanKey,
        element: PsiElement,
        containingClass: com.intellij.psi.PsiClass,
        fieldContext: String? = null
    ): Boolean {
        return forEachApplicable(key) {
            RuleContext.fromMember(project, element, containingClass, fieldContext)
        } ?: false
    }

    suspend fun evaluate(key: RuleKey.BooleanKey, element: PsiElement, contextHandle: (RuleContext) -> Unit): Boolean {
        return forEachApplicable(key) {
            RuleContext.from(project, element).also(contextHandle)
        } ?: false
    }

    suspend fun evaluate(key: RuleKey.IntKey, element: PsiElement): Int? {
        return forEachApplicable(key) { RuleContext.from(project, element) }
    }

    suspend fun evaluate(
        key: RuleKey.IntKey,
        element: PsiElement,
        containingClass: com.intellij.psi.PsiClass
    ): Int? {
        return forEachApplicable(key) { RuleContext.fromMember(project, element, containingClass) }
    }

    suspend fun evaluate(key: RuleKey.EventKey, element: PsiElement, fieldContext: String? = null) {
        forEachApplicable(key) { RuleContext.from(project, element, fieldContext) }
    }

    suspend fun evaluate(
        key: RuleKey.EventKey,
        element: PsiElement,
        containingClass: com.intellij.psi.PsiClass,
        fieldContext: String? = null
    ) {
        forEachApplicable(key) {
            RuleContext.fromMember(project, element, containingClass, fieldContext)
        }
    }

    suspend fun evaluate(key: RuleKey.EventKey, element: PsiElement, contextHandle: (RuleContext) -> Unit) {
        forEachApplicable(key) {
            RuleContext.from(project, element).also(contextHandle)
        }
    }

    suspend fun evaluate(
        key: RuleKey.EventKey,
        element: PsiElement,
        containingClass: com.intellij.psi.PsiClass,
        contextHandle: (RuleContext) -> Unit
    ) {
        forEachApplicable(key) {
            RuleContext.fromMember(project, element, containingClass).also(contextHandle)
        }
    }

    suspend fun evaluate(key: RuleKey.EventKey, contextHandle: (RuleContext) -> Unit = {}) {
        forEachApplicable(key) {
            RuleContext.withoutElement(project).also(contextHandle)
        }
    }

    // ========== Resolved element overloads ==========
    // These accept ResolvedMethod/ResolvedField/ResolvedParam directly.
    // The RuleContext carries the resolved element as `core`, so script contexts
    // can access resolved types (e.g., returnType() returns Result<String> not Result<T>).

    suspend fun evaluate(
        key: RuleKey.StringKey,
        method: com.itangcent.easyapi.core.psi.type.ResolvedMethod,
        fieldContext: String? = null
    ): String? {
        return forEachApplicable(key) { RuleContext.from(project, method, fieldContext) }
    }

    suspend fun evaluate(
        key: RuleKey.StringKey,
        field: com.itangcent.easyapi.core.psi.type.ResolvedField,
        fieldContext: String? = null
    ): String? {
        return forEachApplicable(key) { RuleContext.from(project, field, fieldContext) }
    }

    suspend fun evaluate(
        key: RuleKey.BooleanKey,
        method: com.itangcent.easyapi.core.psi.type.ResolvedMethod,
        fieldContext: String? = null
    ): Boolean {
        return forEachApplicable(key) { RuleContext.from(project, method, fieldContext) } ?: false
    }

    suspend fun evaluate(
        key: RuleKey.BooleanKey,
        field: com.itangcent.easyapi.core.psi.type.ResolvedField,
        fieldContext: String? = null
    ): Boolean {
        return forEachApplicable(key) { RuleContext.from(project, field, fieldContext) } ?: false
    }

    suspend fun evaluate(
        key: RuleKey.EventKey,
        method: com.itangcent.easyapi.core.psi.type.ResolvedMethod,
        fieldContext: String? = null
    ) {
        forEachApplicable(key) { RuleContext.from(project, method, fieldContext) }
    }

    suspend fun evaluate(
        key: RuleKey.EventKey,
        field: com.itangcent.easyapi.core.psi.type.ResolvedField,
        fieldContext: String? = null
    ) {
        forEachApplicable(key) { RuleContext.from(project, field, fieldContext) }
    }

    suspend fun evaluate(
        key: RuleKey.EventKey,
        method: com.itangcent.easyapi.core.psi.type.ResolvedMethod,
        contextHandle: (RuleContext) -> Unit
    ) {
        forEachApplicable(key) {
            RuleContext.from(project, method).also(contextHandle)
        }
    }

    private suspend fun <T> forEachApplicable(
        key: RuleKey<T>,
        ctx: () -> RuleContext
    ): T? {
        val rules = ruleProvider.getRules(key)
        if (rules.isEmpty()) {
            return null
        }
        val ruleContext = ctx()
        val results = flow {
            for ((expression, filter) in rules) {
                ruleContext.regexGroups = null
                val shouldApply = if (filter != null) {
                    runCatching {
                        parse(filter, ruleContext, FILTER_KEY)
                    }.onFailure { e ->
                        // A throwing filter silently disables the rule (false);
                        // record it like a throwing value.
                        ruleContext.console.warn("Filter evaluation failed for key=${key.name}", e)
                        RuleFailureMonitor.getInstance(project).record(key.name, e)
                    }
                        .getOrNull()
                        ?.asBooleanOrNull()
                        ?: false
                } else {
                    true
                }
                if (shouldApply) {
                    try {
                        val result = parse(expression, ruleContext, key)
                        key.emitCastValues(result) { value ->
                            @Suppress("UNCHECKED_CAST")
                            emit(RuleResult.success(value))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // A throwing rule must not be silent: aggregation drops
                        // failures, which would skip endpoints invisibly.
                        // Log per occurrence and record for the
                        // per-run aggregated notification.
                        ruleContext.console.warn("Rule ${key.name} threw during evaluation", e)
                        RuleFailureMonitor.getInstance(project).record(key.name, e)
                        emit(RuleResult.failure(e))
                    }
                }
                ruleContext.regexGroups = null
            }
        }
        return key.mode.aggregate(results)
    }

    /**
     * Converts a script result and invokes [handle] for each typed value,
     * expanding arrays and collections into individual elements.
     *
     * Groovy scripts may return Java arrays (e.g., `String[]`) or collections (e.g., `List`),
     * whose default `toString()` produces unhelpful output like `[Ljava.lang.String;@abc123`.
     * This method expands such results into individual elements so that each element
     * is emitted separately, allowing aggregation modes like
     * [StringRuleMode.MERGE_DISTINCT] to treat each element independently.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> RuleKey<T>.emitCastValues(value: Any?, handle: suspend (T?) -> Unit) {
        when (this) {
            is RuleKey.StringKey -> emitStringValues(value) { handle(it as T?) }
            is RuleKey.BooleanKey -> handle(value.asBooleanOrNull() as T?)
            is RuleKey.IntKey -> handle(value.asInt() as T?)
            is RuleKey.EventKey -> handle(null as T?)
        }
    }

    private suspend fun emitStringValues(value: Any?, handle: suspend (String?) -> Unit) {
        if (value == null) {
            handle(null)
            return
        }
        when (value) {
            is String -> handle(value)
            is Array<*> -> value.forEach { handle(it?.toString()) }
            is Collection<*> -> value.forEach { handle(it?.toString()) }
            else -> handle(value.toString())
        }
    }

    private suspend fun parse(expression: String, ctx: RuleContext, ruleKey: RuleKey<*>? = null): Any? {
        val parser = parsers.firstOrNull { it.canParse(expression) }
        return parser?.parse(expression, ctx, ruleKey)
    }

    companion object {
        private val FILTER_KEY = RuleKey.boolean("__filter__")

        fun getInstance(project: Project): RuleEngine = project.service()
    }
}
