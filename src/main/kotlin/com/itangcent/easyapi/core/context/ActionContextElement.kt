package com.itangcent.easyapi.core.context

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * A coroutine context element that holds a reference to an [ActionContext].
 *
 * This allows [ActionContext] to be accessed from within any coroutine
 * that was launched within that context using [ActionContext.current] or
 * [ActionContext.currentOrNull].
 *
 * ## Usage
 * ```kotlin
 * // Access from within a coroutine
 * suspend fun doWork() {
 *     val ctx = ActionContext.current()
 *     ctx.instance<MyService>().doSomething()
 * }
 * ```
 *
 * @see ActionContext
 */
class ActionContextElement(val context: ActionContext) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ActionContextElement>
}
