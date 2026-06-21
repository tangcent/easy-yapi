package com.itangcent.easyapi.psi.model

import java.util.IdentityHashMap

/**
 * Tracks how many times each [ObjectModel.Object] has been visited during a
 * single traversal, preventing infinite recursion on self-referencing or
 * mutually-referencing types.
 *
 * An object is allowed to be expanded up to [ObjectModel.DEFAULT_MAX_VISITS]
 * times across the whole traversal. On the next attempt [tryEnter] returns
 * `false` and the caller should emit a placeholder (e.g. `{}`, `[]`).
 *
 * The count is restored on [exit] (via `try/finally` inside [withVisit]), so
 * sibling fields that reference the same instance are each allowed their own
 * expansions.
 *
 * ## Usage
 * ```kotlin
 * val tracker = ObjectModelVisitTracker()
 * tracker.withVisit(model) {
 *     // expand model.fields ...
 * } ?: run { sb.append("{}") }   // fallback when limit hit
 * ```
 */
class ObjectModelVisitTracker {

    private val counts = IdentityHashMap<ObjectModel.Object, Int>()

    /** Returns `false` if [model] has already been visited [ObjectModel.DEFAULT_MAX_VISITS] times. */
    fun tryEnter(model: ObjectModel.Object): Boolean {
        val count = counts.getOrDefault(model, 0)
        if (count >= ObjectModel.DEFAULT_MAX_VISITS) return false
        counts[model] = count + 1
        return true
    }

    /** Must be called once for every successful [tryEnter]. */
    fun exit(model: ObjectModel.Object) {
        val count = counts.getOrDefault(model, 0)
        if (count <= 1) counts.remove(model) else counts[model] = count - 1
    }

    /**
     * Runs [block] only if [model] can be entered (i.e. the visit limit has not
     * been reached). Returns the block's result, or `null` if the limit was hit.
     * The visit count is always restored via `try/finally`.
     */
    inline fun <T> withVisit(model: ObjectModel.Object, block: () -> T): T? {
        if (!tryEnter(model)) return null
        return try {
            block()
        } finally {
            exit(model)
        }
    }
}
