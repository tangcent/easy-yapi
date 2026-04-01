package com.itangcent.easyapi.rule

/**
 * Base interface for rule evaluation modes.
 *
 * Determines how multiple rule values are aggregated when
 * the same rule is defined in multiple configuration files.
 */
sealed interface RuleMode

/**
 * Modes for rules that produce string values.
 *
 * Defines how multiple string values are combined.
 */
sealed class StringRuleMode : RuleMode {
    /**
     * Aggregates multiple string values into a single result.
     *
     * @param values The list of values to aggregate
     * @return The aggregated result
     */
    abstract fun aggregate(values: List<String?>): String?

    /**
     * Returns the first non-empty value.
     * Use for rules where only one value should be used.
     */
    data object SINGLE : StringRuleMode() {
        override fun aggregate(values: List<String?>): String? = values.firstOrNull { !it.isNullOrEmpty() }
    }

    /**
     * Merges all non-empty values with newlines.
     * Use for rules where all values should be combined.
     */
    data object MERGE : StringRuleMode() {
        override fun aggregate(values: List<String?>): String? = values.filterNotNull().filter { it.isNotEmpty() }.joinToString("\n").ifEmpty { null }
    }

    /**
     * Merges distinct non-empty values with newlines.
     * Use for rules where duplicate values should be removed.
     */
    data object MERGE_DISTINCT : StringRuleMode() {
        override fun aggregate(values: List<String?>): String? = values.filterNotNull().filter { it.isNotEmpty() }.distinct().joinToString("\n").ifEmpty { null }
    }
}

/**
 * Modes for rules that produce boolean values.
 *
 * Defines how multiple boolean values are combined.
 */
sealed class BooleanRuleMode : RuleMode {
    /**
     * Aggregates multiple boolean values into a single result.
     *
     * @param values The list of values to aggregate
     * @return The aggregated result
     */
    abstract fun aggregate(values: List<Boolean?>): Boolean

    /**
     * Returns true if any value is true.
     * Use for rules like "ignore" or "required".
     */
    data object ANY : BooleanRuleMode() {
        override fun aggregate(values: List<Boolean?>): Boolean = values.any { it == true }
    }

    /**
     * Returns true only if all non-null values are true.
     * Use for rules that require all conditions to be met.
     */
    data object ALL : BooleanRuleMode() {
        override fun aggregate(values: List<Boolean?>): Boolean {
            val nonNull = values.filterNotNull()
            return nonNull.isNotEmpty() && nonNull.all { it }
        }
    }
}

/**
 * Modes for event rules.
 *
 * Event rules are executed for their side effects and don't produce values.
 */
sealed class EventRuleMode : RuleMode {
    /**
     * Whether to throw an exception when an event handler fails.
     */
    abstract val throwOnError: Boolean

    /**
     * Ignores errors and continues execution.
     */
    data object IGNORE_ERROR : EventRuleMode() {
        override val throwOnError: Boolean = false
    }

    /**
     * Throws an exception on error.
     */
    data object THROW_IN_ERROR : EventRuleMode() {
        override val throwOnError: Boolean = true
    }
}

/**
 * Mode for rules that produce integer values.
 *
 * Returns the first non-null value.
 */
data object IntRuleMode : RuleMode {
    /**
     * Returns the first non-null integer value.
     *
     * @param values The list of values to aggregate
     * @return The first non-null value, or null
     */
    fun aggregate(values: List<Int?>): Int? = values.firstOrNull { it != null }
}
