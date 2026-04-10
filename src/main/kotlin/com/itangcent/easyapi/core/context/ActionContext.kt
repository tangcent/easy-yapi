package com.itangcent.easyapi.core.context

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.di.OperationScope
import com.itangcent.easyapi.core.di.get
import com.itangcent.easyapi.core.event.CoroutineEventBus
import com.itangcent.easyapi.core.event.EventKeys
import com.itangcent.easyapi.logging.IdeaConsole
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass

/**
 * The central context for API-related operations in the EasyAPI plugin.
 *
 * ActionContext provides:
 * - Dependency injection via OperationScope
 * - Event bus for inter-component communication
 * - Lifecycle management with automatic cleanup
 *
 * ## Usage
 * ```kotlin
 * // Create context for a project
 * val context = ActionContext.forProject(project)
 *
 * // Access services
 * val settings = context.instance<Settings>()
 * ```
 *
 * @see OperationScope for dependency injection
 * @see ActionContextBuilder for constructing instances
 */
class ActionContext internal constructor(
    @PublishedApi
    internal val operationScope: OperationScope,
) {

    @Volatile
    private var stopped = false

    /**
     * The console instance for logging output.
     */
    val console: IdeaConsole by lazy { operationScope.get(IdeaConsole::class) }

    private val eventBus by lazy { CoroutineEventBus(console = console) }

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
     * Stops this context and fires cleanup events.
     *
     * Fires the [EventKeys.ON_COMPLETED] event for AutoClear handlers.
     */
    fun stop() {
        if (stopped) return
        stopped = true
        runBlocking { eventBus.fire(EventKeys.ON_COMPLETED, this@ActionContext) }
    }

    /**
     * Checks if this context has been stopped.
     *
     * @return true if the context is no longer active
     */
    fun isStopped(): Boolean = stopped

    /**
     * Throws [CancellationException] if this context has been stopped.
     *
     * @throws CancellationException if the context was stopped
     */
    fun checkStatus() {
        if (stopped) throw CancellationException("ActionContext was stopped")
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
            val actualSettings = settings ?: SettingBinder.getInstance(project).read()
            return builder()
                .bind(Project::class, project)
                .withSpiBindings(actualSettings)
                .build()
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
        runCatching { autoClear.cleanup() }.onFailure { e -> console.warn("AutoClear failed", e) }
    }
}
