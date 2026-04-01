package com.itangcent.easyapi.rule.parser

import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.rule.context.RuleContext

/**
 * Fallback parser that returns expressions as literal values.
 *
 * Handles:
 * - `true` / `false` — returns [Boolean] (useful in boolean rule context)
 * - Any other text — returns the string as-is
 *
 * This parser always returns `true` from [canParse], so it must be
 * registered last in the parser chain.
 */
class LiteralParser : RuleParser {

    override fun canParse(expression: String): Boolean = true

    override suspend fun parse(expression: String, context: RuleContext, ruleKey: RuleKey<*>?): Any? {
        val trimmed = expression.trim()
        if (ruleKey is RuleKey.BooleanKey) {
            return when (trimmed.lowercase()) {
                in TRUE_VALS -> {
                    true
                }

                in FALSE_VALS -> {
                    false
                }

                else -> {
                    null
                }
            }
        }
        return trimmed
    }

    companion object {
        private val TRUE_VALS = arrayOf("true", "1")
        private val FALSE_VALS = arrayOf("false", "0")
    }
}
