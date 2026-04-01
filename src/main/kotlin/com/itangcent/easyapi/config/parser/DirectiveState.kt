package com.itangcent.easyapi.config.parser

import java.util.ArrayDeque

/**
 * Strategy for resolving multiple values for the same key.
 */
enum class ResolveMultiMode {
    /** Select the first value. */
    FIRST,

    /** Select the last value. */
    LAST,

    /** Select the longest value. */
    LONGEST,

    /** Select the shortest value. */
    SHORTEST,

    /** Throw/log an error if a key has more than one value. */
    ERROR
}

/**
 * Mutable state for directive processing during configuration parsing.
 *
 * Tracks:
 * - Property resolution options
 * - Conditional block state
 *
 * ## Usage
 * ```kotlin
 * val state = DirectiveState()
 * state.pushCondition(true)  // Start conditional block
 * if (state.isActive()) { ... }  // Check if all conditions are true
 * state.popCondition()  // End conditional block
 * ```
 */
class DirectiveState {
    var resolveProperty: Boolean = true
    var resolveMulti: ResolveMultiMode = ResolveMultiMode.FIRST
    var ignoreNotFoundFile: Boolean = false
    var ignoreUnresolved: Boolean = false
    private val conditionStack = ArrayDeque<Boolean>()

    fun isActive(): Boolean = conditionStack.all { it }

    fun pushCondition(active: Boolean) {
        conditionStack.addLast(active)
    }

    fun popCondition() {
        if (conditionStack.isNotEmpty()) conditionStack.removeLast()
    }
}

/**
 * Immutable snapshot of directive state at the point a [ConfigEntry] was parsed.
 * Carried on each entry so that [LayeredConfigReader] can respect per-entry directives.
 */
data class DirectiveSnapshot(
    val resolveProperty: Boolean = true,
    val resolveMulti: ResolveMultiMode = ResolveMultiMode.FIRST,
    val ignoreUnresolved: Boolean = false
)

/** Captures the current directive values as an immutable snapshot. */
fun DirectiveState.snapshot(): DirectiveSnapshot = DirectiveSnapshot(
    resolveProperty = resolveProperty,
    resolveMulti = resolveMulti,
    ignoreUnresolved = ignoreUnresolved
)