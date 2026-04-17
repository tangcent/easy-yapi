package com.itangcent.easyapi.rule

import kotlinx.coroutines.flow.*

/**
 * Defines how multiple rule values are aggregated into a single result.
 *
 * @param T the type of values being aggregated
 */
sealed interface RuleMode<T> {
    /**
     * Aggregates multiple rule results into a single value.
     *
     * @param values The flow of results to aggregate
     * @return The aggregated result
     */
    suspend fun aggregate(values: Flow<RuleResult<T>>): T?
}

/**
 * Modes for rules that produce string values.
 *
 * Defines how multiple string values are combined.
 */
sealed class StringRuleMode : RuleMode<String> {
    abstract override suspend fun aggregate(values: Flow<RuleResult<String>>): String?

    /**
     * Returns the first non-empty value.
     * Use for rules where only one value should be used.
     */
    data object SINGLE : StringRuleMode() {
        override suspend fun aggregate(values: Flow<RuleResult<String>>): String? =
            values.mapNotNull { it.result }.firstOrNull { it.isNotEmpty() }
    }

    /**
     * Merges all non-empty values with newlines.
     * Use for rules where all values should be combined.
     */
    data object MERGE : StringRuleMode() {
        override suspend fun aggregate(values: Flow<RuleResult<String>>): String? =
            values.mapNotNull { it.result }.filter { it.isNotEmpty() }.toList().joinToString("\n").ifEmpty { null }
    }

    /**
     * Merges distinct non-empty values with newlines.
     * Use for rules where duplicate values should be removed.
     */
    data object MERGE_DISTINCT : StringRuleMode() {
        override suspend fun aggregate(values: Flow<RuleResult<String>>): String? =
            values.mapNotNull { it.result }.filter { it.isNotEmpty() }.toList().distinct().joinToString("\n")
                .ifEmpty { null }
    }
}

/**
 * Modes for rules that produce boolean values.
 *
 * Defines how multiple boolean values are combined.
 */
sealed class BooleanRuleMode : RuleMode<Boolean> {
    abstract override suspend fun aggregate(values: Flow<RuleResult<Boolean>>): Boolean

    /**
     * Returns true if any value is true.
     * Use for rules like "ignore" or "required".
     */
    data object ANY : BooleanRuleMode() {
        override suspend fun aggregate(values: Flow<RuleResult<Boolean>>): Boolean =
            values.mapNotNull { it.result }.firstOrNull { it } != null
    }

    /**
     * Returns true only if all non-null values are true.
     * Use for rules that require all conditions to be met.
     */
    data object ALL : BooleanRuleMode() {
        override suspend fun aggregate(values: Flow<RuleResult<Boolean>>): Boolean {
            val nonNull = values.mapNotNull { it.result }.toList()
            return nonNull.isNotEmpty() && nonNull.all { it }
        }
    }
}

/**
 * Modes for event rules.
 *
 * Event rules are executed for their side effects and don't produce values.
 */
sealed class EventRuleMode : RuleMode<Unit> {

    abstract override suspend fun aggregate(values: Flow<RuleResult<Unit>>): Unit?

    /**
     * Whether to throw an exception when an event handler fails.
     */
    abstract val throwOnError: Boolean

    /**
     * Ignores errors and continues execution.
     */
    data object IGNORE_ERROR : EventRuleMode() {
        override suspend fun aggregate(values: Flow<RuleResult<Unit>>): Unit? {
            values.collect {}
            return null
        }

        override val throwOnError: Boolean = false
    }

    /**
     * Throws an exception on error.
     */
    data object THROW_IN_ERROR : EventRuleMode() {
        override suspend fun aggregate(values: Flow<RuleResult<Unit>>): Unit? {
            var error: Throwable? = null
            values.collect { result ->
                if (result.error != null && error == null) {
                    error = result.error
                }
            }
            error?.let { throw it }
            return null
        }

        override val throwOnError: Boolean = true
    }
}

/**
 * Mode for rules that produce integer values.
 *
 * Returns the first non-null value.
 */
data object IntRuleMode : RuleMode<Int> {
    /**
     * Returns the first non-null integer value.
     *
     * @param values The flow of results to aggregate
     * @return The first non-null value, or null
     */
    override suspend fun aggregate(values: Flow<RuleResult<Int>>): Int? =
        values.mapNotNull { it.result }.firstOrNull()
}

/**
 * Result of a rule evaluation.
 *
 * @param T the type of the result value
 */
interface RuleResult<T> {
    /**
     * The result value, or null if the rule didn't produce a value or failed.
     */
    val result: T?

    /**
     * The error that occurred during evaluation, or null if successful.
     */
    val error: Throwable?

    companion object {
        /**
         * Creates a successful result with the given value.
         */
        fun <T> success(result: T?): RuleResult<T> =
            if (result == null) NULL() else Success(result)

        /**
         * Creates a failure result with the given error.
         */
        fun <T> failure(error: Throwable): RuleResult<T> = Failure(error)

        /**
         * Creates a null result.
         */
        fun <T> NULL() = NullInstance as RuleResult<T>
    }
}

/**
 * A successful rule result.
 */
data class Success<T>(override val result: T) : RuleResult<T> {
    override val error: Throwable?
        get() = null
}

/**
 * A failed rule result.
 */
data class Failure<T>(override val error: Throwable) : RuleResult<T> {
    override val result: T? = null
}

/**
 * A null rule result.
 */
object NullInstance : RuleResult<Any> {
    override val result = null
    override val error: Throwable? = null
}
