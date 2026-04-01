package com.itangcent.easyapi.rule.parser

import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.rule.context.RuleContext

/**
 * Parses `#tag` expressions that reference JavaDoc/KDoc tags.
 *
 * Behavior depends on the expected rule mode:
 * - **String mode**: returns the tag value via `findDocByTag` (e.g., `#mock` → `"@integer"`)
 * - **Boolean mode**: returns whether the tag exists via `hasTag` (e.g., `#mock` → `true`/`false`)
 *
 * This matches the legacy `SimpleRuleParser` behavior where `parseStringRule("#tag")`
 * returns the tag value, while `parseBooleanRule("#tag")` returns tag existence.
 */
class TagExpressionParser : RuleParser {
    override fun canParse(expression: String): Boolean {
        return expression.startsWith("#") && !expression.startsWith("#regex:")
    }

    override suspend fun parse(expression: String, context: RuleContext, ruleKey: RuleKey<*>?): Any? {
        val element = context.element ?: return null
        val tag = expression.removePrefix("#").trim()
        if (tag.isEmpty()) return null

        return if (ruleKey is RuleKey.BooleanKey) {
            // Boolean context: check tag existence
            context.docHelper.hasTag(element, tag)
        } else {
            // String/other context: return tag value (null if tag doesn't exist)
            context.docHelper.findDocByTag(element, tag)
        }
    }
}
