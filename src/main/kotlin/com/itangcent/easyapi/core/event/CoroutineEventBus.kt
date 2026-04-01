package com.itangcent.easyapi.core.event

import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.logging.IdeaConsole
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A simple event bus for coroutine-based event handling within ActionContext.
 *
 * Supports registering handlers for event keys and firing events to all registered handlers.
 * Handlers are suspend functions that receive the ActionContext as parameter.
 *
 * ## Usage
 * ```kotlin
 * val eventBus = CoroutineEventBus(console)
 *
 * // Register a handler
 * eventBus.register("ON_COMPLETED") { context ->
 *     println("Context completed")
 * }
 *
 * // Fire an event
 * eventBus.fire("ON_COMPLETED", context)
 * ```
 *
 * @see EventKeys for predefined event keys
 */
class CoroutineEventBus(private val console: IdeaConsole? = null) {
    private val handlers = ConcurrentHashMap<String, CopyOnWriteArrayList<suspend (ActionContext) -> Unit>>()

    /**
     * Registers a handler for the specified event key.
     *
     * Multiple handlers can be registered for the same key.
     *
     * @param key The event key to listen for
     * @param handler The suspend function to execute when the event fires
     */
    fun register(key: String, handler: suspend (ActionContext) -> Unit) {
        handlers.computeIfAbsent(key) { CopyOnWriteArrayList() }.add(handler)
    }

    /**
     * Fires an event to all registered handlers.
     *
     * Handlers are called sequentially. If a handler throws an exception,
     * it is logged and the next handler continues.
     *
     * @param key The event key to fire
     * @param context The ActionContext to pass to handlers
     */
    suspend fun fire(key: String, context: ActionContext) {
        val list = handlers[key] ?: return
        for (handler in list) {
            try {
                handler(context)
            } catch (e: Exception) {
                console?.warn("Event handler failed for key=$key", e)
            }
        }
    }
}
