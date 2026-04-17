package com.itangcent.easyapi.rule

/**
 * A typed rule key that carries the expected return type and aggregation mode.
 *
 * Use [RuleKeys] for pre-defined keys, or the factory methods to create custom ones:
 * ```kotlin
 * val name: String? = engine.evaluate(RuleKeys.API_NAME, psiMethod)
 * val required: Boolean = engine.evaluate(RuleKeys.FIELD_REQUIRED, psiField)
 * ```
 *
 * @param T the return type of the rule evaluation
 * @param name the primary config key name
 * @param mode the aggregation mode
 * @param aliases alternative config key names that map to this key
 */
sealed class RuleKey<T>(
    val name: String,
    val mode: RuleMode<T>,
    val aliases: List<String> = emptyList()
) {
    /** All config key names (primary + aliases) that should be looked up. */
    val allNames: List<String> get() = listOf(name) + aliases

    class StringKey(
        name: String,
        mode: StringRuleMode = StringRuleMode.SINGLE,
        aliases: List<String> = emptyList()
    ) : RuleKey<String>(name, mode, aliases) {
        val stringMode: StringRuleMode get() = mode as StringRuleMode
    }

    class BooleanKey(
        name: String,
        mode: BooleanRuleMode = BooleanRuleMode.ANY,
        aliases: List<String> = emptyList()
    ) : RuleKey<Boolean>(name, mode, aliases) {
        val booleanMode: BooleanRuleMode get() = mode as BooleanRuleMode
    }

    class IntKey(
        name: String,
        aliases: List<String> = emptyList()
    ) : RuleKey<Int>(name, IntRuleMode, aliases)

    class EventKey(
        name: String,
        mode: EventRuleMode = EventRuleMode.IGNORE_ERROR,
        aliases: List<String> = emptyList()
    ) : RuleKey<Unit>(name, mode, aliases) {
        val eventMode: EventRuleMode get() = mode as EventRuleMode
    }

    override fun toString(): String = name

    companion object {
        fun string(name: String, mode: StringRuleMode = StringRuleMode.SINGLE, aliases: List<String> = emptyList()) =
            StringKey(name, mode, aliases)

        fun boolean(name: String, mode: BooleanRuleMode = BooleanRuleMode.ANY, aliases: List<String> = emptyList()) =
            BooleanKey(name, mode, aliases)

        fun int(name: String, aliases: List<String> = emptyList()) = IntKey(name, aliases)

        fun event(name: String, mode: EventRuleMode = EventRuleMode.IGNORE_ERROR, aliases: List<String> = emptyList()) =
            EventKey(name, mode, aliases)
    }
}
