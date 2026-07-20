package com.itangcent.easyapi.core.rule.parser

import com.itangcent.easyapi.core.rule.RuleKey
import com.itangcent.easyapi.core.rule.context.RuleContext

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
        // Behavior depends on the rule's expected type:
        //  - String rules resolve the annotation attribute value, defaulting to "value"
        //    when no attribute is specified (e.g. `@ConfigurationProperties` reads `value()`).
        //  - Boolean rules check annotation presence when no attribute is specified,
        //    and otherwise resolve the attribute (e.g. `@Deprecated#since`).
        return when (ruleKey) {
            is RuleKey.StringKey -> {
                val attrName = attr.takeUnless { it.isNullOrBlank() } ?: "value"
                helper.findAttr(element, annFqn, attrName)
            }
            is RuleKey.BooleanKey -> {
                if (attr.isNullOrBlank()) {
                    helper.hasAnn(element, annFqn)
                } else {
                    helper.findAttr(element, annFqn, attr)
                }
            }
            else -> {
                if (attr.isNullOrBlank()) {
                    helper.hasAnn(element, annFqn)
                } else {
                    helper.findAttr(element, annFqn, attr)
                }
            }
        }
    }
}
