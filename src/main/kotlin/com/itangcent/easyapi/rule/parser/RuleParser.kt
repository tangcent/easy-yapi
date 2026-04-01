package com.itangcent.easyapi.rule.parser

import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.rule.context.RuleContext
import com.itangcent.easyapi.rule.engine.RuleEngine

/**
 * Parser for rule expressions in the EasyAPI configuration system.
 *
 * Rule expressions are used throughout the plugin for:
 * - Conditional logic (e.g., `field.ignore`)
 * - Value extraction (e.g., `api.name`)
 * - Type matching (e.g., `$class:com.example.Foo`)
 *
 * ## Implementations
 * - [ClassMatchParser] - `$class:` expressions
 * - [AnnotationExpressionParser] - `@Annotation` expressions
 * - [RegexParser] - `~regex` expressions
 * - [LiteralParser] - Literal values
 * - [NegationParser] - `!` negation
 * - [TagExpressionParser] - `#tag` expressions
 * - [Jsr223ScriptParser] - Script-based rules
 *
 * @see RuleEngine for expression evaluation
 */
interface RuleParser {
    /**
     * Checks if this parser can handle the given expression.
     *
     * @param expression The expression to check
     * @return true if this parser can parse the expression
     */
    fun canParse(expression: String): Boolean

    /**
     * Parse and evaluate a rule expression.
     *
     * @param expression the rule expression text
     * @param context the evaluation context
     * @param ruleKey the rule key being evaluated, providing the expected type and mode.
     *               Parsers can use `ruleKey.mode` to return the appropriate type —
     *               e.g., a `#tag` expression returns the tag value for [RuleKey.StringKey],
     *               but tag existence for [RuleKey.BooleanKey].
     *               May be null for ad-hoc evaluations (e.g., filter expressions).
     */
    suspend fun parse(expression: String, context: RuleContext, ruleKey: RuleKey<*>? = null): Any?
}

/**
 * Implement this interface to receive a [RuleEngine] reference after construction.
 *
 * Used by parsers that need to delegate evaluation back to the engine
 * (e.g., [NegationParser] which evaluates the inner expression via the engine).
 */
interface RuleEngineAware {
    fun setRuleEngine(engine: RuleEngine)
}
