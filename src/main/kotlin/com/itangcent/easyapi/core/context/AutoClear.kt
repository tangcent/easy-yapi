package com.itangcent.easyapi.core.context

/**
 * Interface for objects that require cleanup when an [ActionContext] completes.
 *
 * Implementations can be registered with [ActionContext.registerAutoClear] to ensure
 * resources are properly released when the context stops.
 *
 * ## Usage
 * ```kotlin
 * class MyResource : AutoClear {
 *     override suspend fun cleanup() {
 *         // Release resources
 *         closeConnections()
 *     }
 * }
 *
 * // Register for auto-cleanup
 * context.registerAutoClear(myResource)
 * ```
 *
 * @see ActionContext.registerAutoClear
 */
interface AutoClear {
    /**
     * Called when the associated ActionContext completes.
     *
     * Implementations should release any resources held by this object.
     */
    suspend fun cleanup()
}
