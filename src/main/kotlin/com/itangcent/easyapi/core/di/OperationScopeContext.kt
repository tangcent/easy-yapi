package com.itangcent.easyapi.core.di

import kotlinx.coroutines.currentCoroutineContext

/**
 * Retrieves the current OperationScope from the coroutine context.
 *
 * @return The current OperationScope
 * @throws IllegalStateException if no OperationScope is present
 */
suspend fun currentOperationScope(): OperationScope {
    return currentCoroutineContext()[OperationScopeElement]?.scope
        ?: error("No OperationScope in current coroutine context")
}

/**
 * Retrieves an instance of the specified type from the current OperationScope.
 *
 * @param T The type of instance to retrieve
 * @return The instance of type T
 * @throws OperationScopeException if no binding exists for T
 */
suspend inline fun <reified T : Any> scopedInstance(): T {
    return currentOperationScope().get(T::class)
}
