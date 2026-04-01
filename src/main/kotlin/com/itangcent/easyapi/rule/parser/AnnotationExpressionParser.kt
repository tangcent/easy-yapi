package com.itangcent.easyapi.rule.parser

import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.rule.context.RuleContext

/**
 * Parser for annotation-based rule expressions.
 *
 * Supports expressions in the form `@AnnotationFqn` or `@AnnotationFqn#attribute`.
 *
 * ## Examples
 * - `@org.springframework.web.bind.annotation.RequestMapping` - Check annotation presence
 * - `@org.springframework.web.bind.annotation.RequestMapping#path` - Get path attribute
 * - `@RequestMapping#method` - Get method attribute (short name)
 *
 * @see RuleParser for the interface
 */
class AnnotationExpressionParser : RuleParser {
    override fun canParse(expression: String): Boolean = expression.startsWith("@")

    override suspend fun parse(expression: String, context: RuleContext, ruleKey: RuleKey<*>?): Any? {
        val element = context.element ?: return null
        val helper = context.annotationHelper ?: return null
        val expr = expression.removePrefix("@")
        val (annFqn, attr) = expr.split('#', limit = 2).let { parts ->
            parts[0] to parts.getOrNull(1)
        }
        if (annFqn.isBlank()) return null
        if (attr.isNullOrBlank()) {
            return helper.hasAnn(element, annFqn)
        }
        return helper.findAttrAsString(element, annFqn, attr)
    }
}
