package com.itangcent.easyapi.rule

/**
 * Represents a configuration rule with an optional filter expression.
 *
 * Configuration rules are loaded from config sources and evaluated by [RuleEngine].
 * A rule consists of:
 * - [expression]: The actual rule expression to evaluate (e.g., `"groovy: it.name()"`)
 * - [filter]: An optional filter expression that determines if the rule should apply
 *
 * ## Filter Syntax
 * Rules can be indexed with filter expressions in the config file:
 * ```
 * api.name=groovy:it.name()
 * api.name[true]=groovy:it.name().toUpperCase()
 * ```
 * The filter `[true]` is evaluated first; if it returns true, the rule is applied.
 *
 * @property expression The rule expression to evaluate
 * @property filter Optional filter expression; if null, the rule always applies
 * @see RuleProvider
 * @see com.itangcent.easyapi.rule.engine.RuleEngine
 */
data class ConfigRule(
    val expression: String,
    val filter: String? = null
)
