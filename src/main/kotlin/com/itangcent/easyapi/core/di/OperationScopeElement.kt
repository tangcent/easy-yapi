package com.itangcent.easyapi.core.di

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * A coroutine context element that holds a reference to an [OperationScope].
 *
 * This allows [OperationScope] to be accessed from within any coroutine
 * that was launched within that context using [currentOperationScope].
 *
 * @see OperationScope
 * @see currentOperationScope
 */
class OperationScopeElement(val scope: OperationScope) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<OperationScopeElement>
}
