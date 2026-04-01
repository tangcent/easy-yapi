package com.itangcent.easyapi.core.context

import com.itangcent.easyapi.core.di.OperationScope
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.settings.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlin.reflect.KClass

/**
 * Builder for constructing [ActionContext] instances.
 *
 * Provides a fluent API for configuring and creating ActionContext instances
 * with custom bindings, dispatchers, and lifecycle settings.
 *
 * ## Usage
 * ```kotlin
 * val context = ActionContext.builder()
 *     .bind(project)
 *     .bind(myService)
 *     .withSpiBindings(settings)
 *     .dispatcher(IdeDispatchers.Background)
 *     .build()
 * ```
 *
 * @see ActionContext
 * @see OperationScope.Builder
 */
class ActionContextBuilder {
    private val scopeBuilder = OperationScope.builder()
    private var parentJob: Job? = null
    private var dispatcher: CoroutineDispatcher = IdeDispatchers.Background

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
     * This includes services like ConfigReader, CacheService, AnnotationHelper, etc.
     *
     * @param settings Optional settings for filtering SPI implementations
     * @return This builder for chaining
     */
    fun withSpiBindings(settings: Settings? = null): ActionContextBuilder = apply {
        scopeBuilder.addSpiBindings(settings)
    }

    /**
     * Sets a custom parent job for the context's coroutine scope.
     *
     * @param job The parent job
     * @return This builder for chaining
     */
    fun parentJob(job: Job): ActionContextBuilder = apply {
        parentJob = job
    }

    /**
     * Sets a custom dispatcher for the context's coroutine scope.
     *
     * @param dispatcher The coroutine dispatcher
     * @return This builder for chaining
     */
    fun dispatcher(dispatcher: CoroutineDispatcher): ActionContextBuilder = apply {
        this.dispatcher = dispatcher
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
        return ActionContext(operationScope, parentJob ?: SupervisorJob(), dispatcher)
            .also { context = it }
    }
}
