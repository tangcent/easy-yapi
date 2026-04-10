package com.itangcent.easyapi.core.context

import com.itangcent.easyapi.core.di.OperationScope
import com.itangcent.easyapi.settings.Settings
import kotlin.reflect.KClass

/**
 * Builder for constructing [ActionContext] instances.
 *
 * Provides a fluent API for configuring and creating ActionContext instances
 * with custom bindings.
 *
 * ## Usage
 * ```kotlin
 * val context = ActionContext.builder()
 *     .bind(project)
 *     .bind(myService)
 *     .withSpiBindings(settings)
 *     .build()
 * ```
 *
 * @see ActionContext
 * @see OperationScope.Builder
 */
class ActionContextBuilder {
    private val scopeBuilder = OperationScope.builder()

    /**
     * Binds an instance to its own type in the operation scope.
     *
     * @param T The type of the instance
     * @param instance The instance to bind
     * @return This builder for chaining
     */
    fun <T : Any> bind(instance: T): ActionContextBuilder = apply {
        scopeBuilder.bind(instance)
    }

    /**
     * Binds an instance to a specific type in the operation scope.
     *
     * @param T The type to bind to
     * @param kClass The KClass of the type
     * @param instance The instance to bind
     * @return This builder for chaining
     */
    fun <T : Any> bind(kClass: KClass<T>, instance: T): ActionContextBuilder = apply {
        scopeBuilder.bind(kClass, instance)
    }

    /**
     * Binds a lazy provider to a type in the operation scope.
     *
     * The provider will be called only when the type is first requested.
     *
     * @param T The type to bind to
     * @param kClass The KClass of the type
     * @param provider The function that provides the instance
     * @return This builder for chaining
     */
    fun <T : Any> bindLazy(kClass: KClass<T>, provider: () -> T): ActionContextBuilder = apply {
        scopeBuilder.bindLazy(kClass, provider)
    }

    /**
     * Adds standard SPI bindings to the operation scope.
     *
     * This includes services like ConfigReader, AnnotationHelper, etc.
     *
     * @param settings Optional settings for filtering SPI implementations
     * @return This builder for chaining
     */
    fun withSpiBindings(settings: Settings? = null): ActionContextBuilder = apply {
        scopeBuilder.addSpiBindings(settings)
    }

    /**
     * Builds and returns a new ActionContext instance.
     *
     * @return A new ActionContext configured with the builder's settings
     */
    fun build(): ActionContext {
        var context: ActionContext? = null
        scopeBuilder.bindLazy(ActionContext::class) {
            context ?: error("ActionContext not yet initialized")
        }
        val operationScope = scopeBuilder.build()
        return ActionContext(operationScope).also { context = it }
    }
}
