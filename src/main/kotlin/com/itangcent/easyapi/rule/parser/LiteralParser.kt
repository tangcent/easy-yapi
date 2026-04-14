package com.itangcent.easyapi.rule.parser

import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.rule.context.RuleContext

/**
 * Fallback parser that returns expressions as literal values.
 *
 * Handles:
 * - `true` / `false` / `yes` / `no` / `y` / `n` / `1` / `0` — returns [Boolean] (useful in boolean rule context)
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
        // Resolve ${n} placeholders from regex groups captured during filter evaluation
        val groups = context.regexGroups
        if (groups != null && PLACEHOLDER_PATTERN.containsMatchIn(trimmed)) {
            return PLACEHOLDER_PATTERN.replace(trimmed) { match ->
                val groupIndex = match.groupValues[1].toIntOrNull()
                if (groupIndex != null && groupIndex in 1..groups.size) {
                    groups[groupIndex - 1]
                } else {
                    match.value
                }
            }
        }
        return trimmed
    }

    companion object {
        private val TRUE_VALS = arrayOf("true", "1", "yes", "y")
        private val FALSE_VALS = arrayOf("false", "0", "no", "n")
        private val PLACEHOLDER_PATTERN = Regex("\\$\\{(\\d+)}")
    }
}
