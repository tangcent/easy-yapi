package com.itangcent.idea.plugin.rule

import com.google.inject.Inject
import com.itangcent.intellij.config.rule.Rule
import com.itangcent.intellij.config.rule.RuleContext
import com.itangcent.intellij.config.rule.RuleParser
import com.itangcent.intellij.config.rule.SimpleRuleParser
import com.itangcent.intellij.context.ActionContext
import kotlin.reflect.KClass

/**
 * [js:] -> [JsRuleParser]
 * [groovy:] -> [GroovyRuleParser]
 * [@]|[#] ->  [SimpleRuleParser]
 */
class SuvRuleParser : RuleParser {

    @Inject
    private val actionContext: ActionContext? = null

    override fun contextOf(target: Any, context: com.intellij.psi.PsiElement?): RuleContext {
        if (target is RuleContext) {
            return target
        }
        return getRuleParser(SimpleRuleParser::class).contextOf(target, context)
    }

    override fun parseRule(rule: String, targetType: KClass<*>): Rule<Any>? {
        return when {
            rule.isBlank() -> null
            rule.startsWith(JS_PREFIX) -> getRuleParser(JsRuleParser::class).parseRule(
                rule.removePrefix(
                    JS_PREFIX
                ), targetType
            )

            rule.startsWith(GROOVY_PREFIX) -> getRuleParser(GroovyRuleParser::class).parseRule(
                rule.removePrefix(
                    GROOVY_PREFIX
                ), targetType
            )

            rule.startsWith(FIELD_PREFIX) -> getRuleParser(FieldPatternRuleParser::class).parseRule(
                rule.removePrefix(
                    FIELD_PREFIX
                ), targetType
            )

            else -> getRuleParser(SimpleRuleParser::class).parseRule(rule, targetType)
        }
    }

    private val ruleParserCache: HashMap<KClass<*>, RuleParser> = LinkedHashMap()

    private fun getRuleParser(parserClass: KClass<*>): RuleParser {
        var ruleParser = ruleParserCache[parserClass]
        if (ruleParser != null) return ruleParser
        synchronized(this) {
            ruleParser = ruleParserCache[parserClass]
            if (ruleParser != null) return ruleParser!!
            ruleParser = actionContext!!.instance(parserClass) as? RuleParser
            if (ruleParser == null) {
                throw IllegalArgumentException("error to build rule parser:${parserClass.qualifiedName}")
            }
            ruleParserCache[parserClass] = ruleParser!!
            return ruleParser!!
        }
    }

    companion object {
        private const val JS_PREFIX = "js:"
        private const val GROOVY_PREFIX = "groovy:"
        private const val FIELD_PREFIX = "field:"
    }
}