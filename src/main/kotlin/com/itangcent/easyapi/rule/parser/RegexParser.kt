package com.itangcent.easyapi.rule.parser

import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.rule.context.RuleContext
import java.util.regex.Pattern

/**
 * Parser for regex-based rule expressions.
 *
 * Matches element text against a regular expression pattern.
 * Expressions must start with `#regex:` prefix.
 *
 * ## Usage
 * ```
 * # Check if element contains "api"
 * #regex:.*api.*
 *
 * # Match URL pattern
 * #regex:/api/v\d+/.*
 * ```
 *
 * @see RuleParser for the parser interface
 */
class RegexParser : RuleParser {
    override fun canParse(expression: String): Boolean = expression.startsWith(PREFIX)

    override suspend fun parse(expression: String, context: RuleContext, ruleKey: RuleKey<*>?): Any? {
        val element = context.element ?: return false
        val pattern = expression.removePrefix(PREFIX)
        if (pattern.isEmpty()) return false
        return runCatching {
            Pattern.compile(pattern, Pattern.DOTALL).matcher(element.text ?: "").find()
        }.getOrDefault(false)
    }

    companion object {
        private const val PREFIX = "#regex:"
    }
}
