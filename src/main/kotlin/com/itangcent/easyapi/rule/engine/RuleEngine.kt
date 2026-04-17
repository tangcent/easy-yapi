package com.itangcent.easyapi.rule.engine

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.rule.RuleProvider
import com.itangcent.easyapi.rule.RuleResult
import com.itangcent.easyapi.rule.context.RuleContext
import com.itangcent.easyapi.rule.parser.*
import com.itangcent.easyapi.util.asBooleanOrNull
import com.itangcent.easyapi.util.asInt
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

    suspend fun evaluate(key: RuleKey.StringKey, element: PsiElement, contextHandle: (RuleContext) -> Unit): String? {
        return forEachApplicable(key) {
            RuleContext.from(project, element).also(contextHandle)
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

    suspend fun evaluate(key: RuleKey.BooleanKey, element: PsiElement, fieldContext: String? = null): Boolean {
        return forEachApplicable(key) { RuleContext.from(project, element, fieldContext) } ?: false
    }

    suspend fun evaluate(key: RuleKey.BooleanKey, element: PsiElement, contextHandle: (RuleContext) -> Unit): Boolean {
        return forEachApplicable(key) {
            RuleContext.from(project, element).also(contextHandle)
        } ?: false
    }

    suspend fun evaluate(key: RuleKey.IntKey, element: PsiElement): Int? {
        return forEachApplicable(key) { RuleContext.from(project, element) }
    }

    suspend fun evaluate(key: RuleKey.EventKey, element: PsiElement, fieldContext: String? = null) {
        forEachApplicable(key) { RuleContext.from(project, element, fieldContext) }
    }

    suspend fun evaluate(key: RuleKey.EventKey, element: PsiElement, contextHandle: (RuleContext) -> Unit) {
        forEachApplicable(key) {
            RuleContext.from(project, element).also(contextHandle)
        }
    }

    suspend fun evaluate(key: RuleKey.EventKey, contextHandle: (RuleContext) -> Unit = {}) {
        forEachApplicable(key) {
            RuleContext.withoutElement(project).also(contextHandle)
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
                    }.onFailure { e -> ruleContext.console.warn("Filter evaluation failed for key=${key.name}", e) }
                        .getOrNull()
                        ?.asBooleanOrNull()
                        ?: false
                } else {
                    true
                }
                if (shouldApply) {
                    try {
                        val result = parse(expression, ruleContext, key)
                        @Suppress("UNCHECKED_CAST")
                        emit(RuleResult.success(key.castValue(result)))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emit(RuleResult.failure(e))
                    }
                }
                ruleContext.regexGroups = null
            }
        }
        return key.mode.aggregate(results)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> RuleKey<T>.castValue(value: Any?): T? {
        return when (this) {
            is RuleKey.StringKey -> value?.toString()
            is RuleKey.BooleanKey -> value.asBooleanOrNull()
            is RuleKey.IntKey -> value.asInt()
            is RuleKey.EventKey -> null
        } as T?
    }

    private suspend fun parse(expression: String, ctx: RuleContext, ruleKey: RuleKey<*>? = null): Any? {
        val parser = parsers.firstOrNull { it.canParse(expression) }
        return parser?.parse(expression, ctx, ruleKey)
    }

    private fun toBoolean(value: Any): Boolean = value.asBooleanOrNull() ?: false

    companion object {
        private val FILTER_KEY = RuleKey.boolean("__filter__")

        fun getInstance(project: Project): RuleEngine = project.service()
    }
}
