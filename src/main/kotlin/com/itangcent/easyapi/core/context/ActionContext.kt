package com.itangcent.easyapi.core.context

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.di.OperationScope
import com.itangcent.easyapi.core.di.OperationScopeElement
import com.itangcent.easyapi.core.di.get
import com.itangcent.easyapi.core.event.CoroutineEventBus
import com.itangcent.easyapi.core.event.EventKeys
import com.itangcent.easyapi.logging.IdeaConsole
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.settings.Settings
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

/**
 * The central context for executing API-related operations in the EasyAPI plugin.
 *
 * ActionContext combines [CoroutineScope] with [OperationScope] to provide:
 * - Structured concurrency for async operations
 * - Dependency injection via OperationScope
 * - Event bus for inter-component communication
 * - Lifecycle management with automatic cleanup
 *
 * ## Usage
 * ```kotlin
 * // Create context for a project
 * val context = ActionContext.forProject(project)
 *
 * // Run async operations
 * context.runAsync {
 *     val settings = instance<Settings>()
 *     // ... do work
 * }
 *
 * // Access from within coroutine
 * suspend fun doWork() {
 *     val ctx = ActionContext.current()
 *     ctx.instance<ConfigReader>().getFirst("api.name")
 * }
 * ```
 *
 * @see OperationScope for dependency injection
 * @see ActionContextBuilder for constructing instances
 */
class ActionContext internal constructor(
    @PublishedApi
    internal val operationScope: OperationScope,
    private val parentJob: Job,
    dispatcher: CoroutineDispatcher
) : CoroutineScope {

    override val coroutineContext: CoroutineContext =
        parentJob + dispatcher +
                ActionContextElement(this) +
                OperationScopeElement(operationScope) +
                CoroutineName("ActionContext")

    /**
     * The console instance for logging output.
     */
    val console: IdeaConsole get() = operationScope.get(IdeaConsole::class)

    private val eventBus by lazy { CoroutineEventBus(console = console) }

    /**
     * Launches an asynchronous coroutine within this context.
     *
     * @param block The suspend function to execute
     * @return The Job representing the launched coroutine
     */
    fun runAsync(block: suspend CoroutineScope.() -> Unit): Job = launch { block() }

    /**
     * Registers an event handler for the specified event key.
     *
     * @param key The event key to listen for
     * @param handler The suspend function to execute when the event fires
     */
    fun on(key: String, handler: suspend ActionContext.() -> Unit) {
        eventBus.register(key) { ctx -> handler(ctx) }
    }

    /**
     * Stops this context and cancels all running coroutines.
     *
     * Fires the [EventKeys.ON_COMPLETED] event before cancellation.
     */
    suspend fun stop() {
        parentJob.cancel(CancellationException("ActionContext stopped"))
        eventBus.fire(EventKeys.ON_COMPLETED, this)
    }

    /**
     * Checks if this context has been stopped.
     *
     * @return true if the context is no longer active
     */
    fun isStopped(): Boolean = !parentJob.isActive

    /**
     * Throws [CancellationException] if this context has been stopped.
     *
     * @throws CancellationException if the context was stopped
     */
    fun checkStatus() {
        if (isStopped()) throw CancellationException("ActionContext was stopped")
    }

    /**
     * Retrieves an instance of the specified type from the operation scope.
     *
     * @param T The type of instance to retrieve
     * @return The instance of type T
     * @throws OperationScopeException if no binding exists for T
     */
    inline fun <reified T : Any> instance(): T = operationScope.get()

    /**
     * Retrieves an instance of the specified class from the operation scope.
     *
     * @param T The type of instance to retrieve
     * @param kClass The KClass of the type
     * @return The instance of type T
     * @throws OperationScopeException if no binding exists for T
     */
    fun <T : Any> instance(kClass: KClass<T>): T = operationScope.get(kClass)

    /**
     * Retrieves an instance of the specified type, or null if not bound.
     *
     * @param T The type of instance to retrieve
     * @return The instance of type T, or null if not bound
     */
    inline fun <reified T : Any> instanceOrNull(): T? = operationScope.getOrNull(T::class)

    /**
     * Retrieves an instance of the specified class, or null if not bound.
     *
     * @param T The type of instance to retrieve
     * @param kClass The KClass of the type
     * @return The instance of type T, or null if not bound
     */
    fun <T : Any> instanceOrNull(kClass: KClass<T>): T? = operationScope.getOrNull(kClass)

    companion object : IdeaLog {

        /**
         * A shared ActionContext instance for operations that don't require project-specific bindings.
         */
        val shared: ActionContext by lazy { builder().build() }

        /**
         * Creates a new builder for constructing ActionContext instances.
         *
         * @return A new ActionContextBuilder
         */
        fun builder(): ActionContextBuilder = ActionContextBuilder()

        /**
         * Creates an ActionContext configured for a specific project.
         *
         * This automatically binds the Project, SettingBinder, and SPI services.
         *
         * @param project The IntelliJ project
         * @param settings Optional settings to use; if null, reads from SettingBinder
         * @return A configured ActionContext for the project
         */
        fun forProject(
            project: Project,
            settings: Settings? = null
        ): ActionContext {
            val settingBinder = SettingBinder.getInstance(project)
            val actualSettings = settings ?: settingBinder.read()
            return builder()
                .bind(Project::class, project)
                .bind(SettingBinder::class, settingBinder)
                .withSpiBindings(actualSettings)
                .build()
        }

        /**
         * Retrieves the current ActionContext from the coroutine context.
         *
         * @return The current ActionContext
         * @throws IllegalStateException if no ActionContext is present
         */
        suspend fun current(): ActionContext {
            return currentCoroutineContext()[ActionContextElement]?.context
                ?: error("No ActionContext in current coroutine context")
        }

        /**
         * Retrieves the current ActionContext from the coroutine context, or null if not present.
         *
         * @return The current ActionContext, or null if not present
         */
        suspend fun currentOrNull(): ActionContext? {
            return currentCoroutineContext()[ActionContextElement]?.context
        }
    }
}

/**
 * Retrieves the Project from this context
 *
 * @return The Project instance
 */
fun ActionContext.project(): Project = instance()

/**
 * Retrieves the Project from this context, or null if not bound.
 *
 * @return The Project instance, or null
 */
fun ActionContext.projectOrNull(): Project? = instanceOrNull()

/**
 * Registers an AutoClear handler to be executed when the context completes.
 *
 * The cleanup will be called when [EventKeys.ON_COMPLETED] is fired.
 *
 * @param autoClear The cleanup handler to register
 */
fun ActionContext.registerAutoClear(autoClear: AutoClear) {
    on(EventKeys.ON_COMPLETED) {
        runCatching { autoClear.cleanup() }.onFailure { e -> console?.warn("AutoClear failed", e) }
    }
}
