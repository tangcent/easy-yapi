package com.itangcent.idea.plugin.rule

import com.google.inject.Inject
import com.intellij.psi.PsiElement
import com.itangcent.intellij.config.rule.*
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.PostConstruct

/**
 * [js:] -> jsRule
 * [@]|[#] ->  simpleRule
 */
class SuvRuleParser : RuleParser {

    private val jsRuleParser: RuleParser = JsRuleParser()

    private val groovyRuleParser: RuleParser = GroovyRuleParser()

    private val simpleRuleParser: RuleParser = SimpleRuleParser()

    @Inject
    private val actionContext: ActionContext? = null

    @PostConstruct
    fun init() {
        actionContext!!.init(jsRuleParser)
        actionContext.init(groovyRuleParser)
        actionContext.init(simpleRuleParser)
    }

    override fun contextOf(psiElement: PsiElement): PsiElementContext {
        return simpleRuleParser.contextOf(psiElement)
    }

    override fun parseBooleanRule(rule: String): List<BooleanRule> {
        return when {
            rule.isBlank() -> emptyList()
            rule.startsWith(JS_PREFIX) -> jsRuleParser.parseBooleanRule(rule.removePrefix(JS_PREFIX))
            rule.startsWith(GROOVY_PREFIX) -> groovyRuleParser.parseBooleanRule(rule.removePrefix(GROOVY_PREFIX))
            else -> simpleRuleParser.parseBooleanRule(rule)
        }
    }

    override fun parseBooleanRule(rule: String, delimiters: String, defaultValue: Boolean): List<BooleanRule> {
        return simpleRuleParser.parseBooleanRule(rule, delimiters, defaultValue)
    }

    override fun parseStringRule(rule: String): List<StringRule> {
        return when {
            rule.isBlank() -> emptyList()
            rule.startsWith(JS_PREFIX) -> jsRuleParser.parseStringRule(rule.removePrefix(JS_PREFIX))
            rule.startsWith(GROOVY_PREFIX) -> groovyRuleParser.parseStringRule(rule.removePrefix(GROOVY_PREFIX))
            else -> simpleRuleParser.parseStringRule(rule)
        }
    }

    override fun parseStringRule(rule: String, delimiters: String): List<StringRule> {
        return simpleRuleParser.parseStringRule(rule, delimiters)
    }

    companion object {
        private const val JS_PREFIX = "js:"
        private const val GROOVY_PREFIX = "groovy:"
    }
}