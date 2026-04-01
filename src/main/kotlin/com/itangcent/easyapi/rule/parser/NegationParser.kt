package com.itangcent.easyapi.rule.parser

import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.rule.context.RuleContext
import com.itangcent.easyapi.rule.engine.RuleEngine

/**
 * Parses `!expression` negation.
 *
 * Delegates the inner expression to the [RuleEngine] for evaluation,
 * then negates the boolean result.
 *
 * Implements [RuleEngineAware] to receive the engine reference.
 */
class NegationParser : RuleParser, RuleEngineAware {

    private lateinit var ruleEngine: RuleEngine

    override fun setRuleEngine(engine: RuleEngine) {
        this.ruleEngine = engine
    }

    override fun canParse(expression: String): Boolean =
        expression.trim().startsWith("!")

    override suspend fun parse(expression: String, context: RuleContext, ruleKey: RuleKey<*>?): Any? {
        val inner = expression.trim().removePrefix("!").trim()
        if (inner.isEmpty()) return false
        // Evaluate the inner expression as a boolean key
        val boolKey = RuleKey.boolean("!")
        return when (val result = ruleEngine.parseExpression(inner, context, boolKey)) {
            is Boolean -> !result
            is Number -> result.toInt() == 0
            is String -> !(result.equals("true", true) || result == "1")
            null -> true
            else -> false
        }
    }
}
