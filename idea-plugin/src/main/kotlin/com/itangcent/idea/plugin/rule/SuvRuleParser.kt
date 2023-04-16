package com.itangcent.idea.plugin.rule

import com.google.inject.Inject
import com.itangcent.intellij.config.rule.*
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

    override fun parseBooleanRule(rule: String): BooleanRule? {
        return when {
            rule.isBlank() -> null
            rule.startsWith(JS_PREFIX) -> getRuleParser(JsRuleParser::class).parseBooleanRule(
                rule.removePrefix(
                    JS_PREFIX
                )
            )

            rule.startsWith(GROOVY_PREFIX) -> getRuleParser(GroovyRuleParser::class).parseBooleanRule(
                rule.removePrefix(
                    GROOVY_PREFIX
                )
            )

            rule.startsWith(FIELD_PREFIX) -> getRuleParser(FieldPatternRuleParser::class).parseBooleanRule(
                rule.removePrefix(
                    FIELD_PREFIX
                )
            )

            else -> getRuleParser(SimpleRuleParser::class).parseBooleanRule(rule)
        }
    }

    override fun parseStringRule(rule: String): StringRule? {
        return when {
            rule.isBlank() -> null
            rule.startsWith(JS_PREFIX) -> getRuleParser(JsRuleParser::class).parseStringRule(rule.removePrefix(JS_PREFIX))
            rule.startsWith(GROOVY_PREFIX) -> getRuleParser(GroovyRuleParser::class).parseStringRule(
                rule.removePrefix(
                    GROOVY_PREFIX
                )
            )

            else -> getRuleParser(SimpleRuleParser::class).parseStringRule(rule)
        }
    }

    override fun parseEventRule(rule: String): EventRule? {
        return when {
            rule.isBlank() -> null
            rule.startsWith(JS_PREFIX) -> getRuleParser(JsRuleParser::class).parseEventRule(rule.removePrefix(JS_PREFIX))
            rule.startsWith(GROOVY_PREFIX) -> getRuleParser(GroovyRuleParser::class).parseEventRule(
                rule.removePrefix(
                    GROOVY_PREFIX
                )
            )

            else -> getRuleParser(SimpleRuleParser::class).parseEventRule(rule)
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