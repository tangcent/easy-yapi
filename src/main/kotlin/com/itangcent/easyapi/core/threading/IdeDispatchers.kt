package com.itangcent.easyapi.core.threading

import com.intellij.openapi.application.ApplicationManager
import com.itangcent.easyapi.core.threading.IdeDispatchers.Background
import com.itangcent.easyapi.core.threading.IdeDispatchers.ReadAction
import com.itangcent.easyapi.core.threading.IdeDispatchers.Swing
import com.itangcent.easyapi.core.threading.IdeDispatchers.WriteAction
import com.itangcent.easyapi.logging.IdeaLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Provides coroutine dispatchers tailored for IntelliJ Platform development.
 *
 * This object provides dispatchers that handle IntelliJ's threading model:
 * - [ReadAction] for PSI/VFS read operations
 * - [WriteAction] for PSI/VFS write operations
 * - [Swing] for UI operations on the Event Dispatch Thread
 * - [Background] for general-purpose background work
 *
 * ## Usage
 * ```kotlin
 * // Using dispatchers directly
 * withContext(IdeDispatchers.ReadAction) {
 *     // Read PSI data
 * }
 *
 * // Using async convenience functions (suspend)
 * read {
 *     // Read PSI data
 * }
 *
 * write {
 *     // Modify PSI data
 * }
 *
 * swing {
 *     // Update UI
 * }
 *
 * // Using sync convenience functions (non-suspend, returns result)
 * val className = readSync {
 *     cls.qualifiedName ?: ""
 * }
 *
 * writeSync {
 *     // Modify PSI data synchronously
 * }
 *
 * swingSync {
 *     // Update UI synchronously
 * }
 *
 * // Using async convenience functions (fire-and-forget)
 * readAsync {
 *     // Read PSI data asynchronously
 * }
 *
 * writeAsync {
 *     // Modify PSI data asynchronously
 * }
 *
 * swingAsync {
 *     // Update UI asynchronously
 * }
 * ```
 *
 * @see ReadActionDispatcher
 * @see WriteActionDispatcher
 * @see SwingDispatcher
 */
object IdeDispatchers : IdeaLog {
    /**
     * Dispatcher for read operations on IntelliJ data structures.
     * Use this for PSI reads, VFS access, and other read-only operations.
     */
    val ReadAction: CoroutineDispatcher = ReadActionDispatcher()

    /**
     * Dispatcher for write operations on IntelliJ data structures.
     * Use this for PSI modifications, VFS changes, and other write operations.
     */
    val WriteAction: CoroutineDispatcher = WriteActionDispatcher()

    /**
     * Dispatcher for UI operations on the Swing Event Dispatch Thread.
     * Use this for updating UI components, showing dialogs, etc.
     */
    val Swing: CoroutineDispatcher = SwingDispatcher()

    /**
     * A clean thread pool that bypasses IntelliJ's ChildContext propagation.
     *
     * IntelliJ wraps all tasks submitted to its managed executors (including
     * executeOnPooledThread, Dispatchers.Default) with ContextRunnable/ContextCallable,
     * which propagates EDT/write-intent markers across thread boundaries.
     * This executor is outside IntelliJ's instrumentation, so tasks run with
     * a clean thread context.
     */
    private val backgroundExecutor = java.util.concurrent.Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "EasyAPI-background").apply { isDaemon = true }
    }

    /**
     * Background dispatcher for general-purpose work.
     *
     * Uses a custom executor to avoid IntelliJ's ChildContext propagation,
     * and sets the thread context classloader to the plugin's classloader,
     * avoiding [java.util.ServiceLoader] failures when kotlinx.coroutines
     * tries to load IntelliJ's SPI entries.
     */
    val Background: CoroutineDispatcher = PluginClassLoaderDispatcher(backgroundExecutor.asCoroutineDispatcher())

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        LOG.warn("Uncaught coroutine exception in Background", throwable)
    }

    private val scope = CoroutineScope(SupervisorJob() + Background + exceptionHandler)

    /**
     * Checks if the current thread has read access to IntelliJ data structures.
     */
    val isReadAccessAllowed: Boolean
        get() = ApplicationManager.getApplication().isReadAccessAllowed

    /**
     * Checks if the current thread has write access to IntelliJ data structures.
     */
    val isWriteAccessAllowed: Boolean
        get() = ApplicationManager.getApplication().isWriteAccessAllowed

    /**
     * Checks if the current thread is the Event Dispatch Thread.
     */
    val isDispatchThread: Boolean
        get() = ApplicationManager.getApplication().isDispatchThread

    /**
     * Executes a block under read access, switching to [ReadAction] dispatcher if needed.
     *
     * If the current thread already has read access, executes immediately.
     * Otherwise, switches to the ReadAction dispatcher.
     *
     * @param T The return type
     * @param block The block to execute
     * @return The result of the block
     */
    suspend fun <T> readAction(block: suspend () -> T): T {
        return if (isReadAccessAllowed) {
            block()
        } else {
            withContext(ReadAction) { block() }
        }
    }

    /**
     * Executes a block under write access, switching to [WriteAction] dispatcher if needed.
     *
     * If the current thread already has write access, executes immediately.
     * Otherwise, switches to the WriteAction dispatcher.
     *
     * @param T The return type
     * @param block The block to execute
     * @return The result of the block
     */
    suspend fun <T> writeAction(block: suspend () -> T): T {
        return if (isWriteAccessAllowed) {
            block()
        } else {
            withContext(WriteAction) { block() }
        }
    }

    /**
     * Executes a block on the EDT, switching to [Swing] dispatcher if needed.
     *
     * If the current thread is already the EDT, executes immediately.
     * Otherwise, switches to the Swing dispatcher.
     *
     * @param T The return type
     * @param block The block to execute
     * @return The result of the block
     */
    suspend fun <T> swing(block: suspend () -> T): T {
        return if (isDispatchThread) {
            block()
        } else {
            withContext(Swing) { block() }
        }
    }

    /**
     * Executes a block synchronously under read access.
     *
     * This is a non-suspending version of [readAction] that can be called from any thread.
     * Uses [Application.runReadAction] internally.
     *
     * @param T The return type
     * @param block The block to execute
     * @return The result of the block
     */
    fun <T> readSync(block: () -> T): T {
        return if (isReadAccessAllowed) {
            block()
        } else {
            ApplicationManager.getApplication().runReadAction<T>(block)
        }
    }

    /**
     * Executes a block synchronously under write access.
     *
     * This is a non-suspending version of [writeAction] that can be called from any thread.
     * Uses [Application.runWriteAction] internally.
     *
     * @param T The return type
     * @param block The block to execute
     * @return The result of the block
     */
    fun <T> writeSync(block: () -> T): T {
        return if (isWriteAccessAllowed) {
            block()
        } else {
            ApplicationManager.getApplication().runWriteAction<T>(block)
        }
    }

    /**
     * Executes a block synchronously on the EDT.
     *
     * This is a non-suspending version of [swing] that can be called from any thread.
     * If already on EDT, executes immediately. Otherwise, uses [Application.invokeAndWait].
     *
     * @param T The return type
     * @param block The block to execute
     * @return The result of the block
     */
    fun <T> swingSync(block: () -> T): T {
        return if (isDispatchThread) {
            block()
        } else {
            var result: T? = null
            ApplicationManager.getApplication().invokeAndWait { result = block() }
            @Suppress("UNCHECKED_CAST")
            result as T
        }
    }

    /**
     * Executes a suspend block on a background thread using the [Background] dispatcher.
     *
     * @param T The return type
     * @param block The suspend block to execute
     * @return The result of the block
     */
    suspend fun <T> background(block: suspend () -> T): T = withContext(Background) { block() }

    /**
     * Executes a suspend block on a clean background thread, free of any
     * inherited EDT/write-intent context from IntelliJ's ChildContext propagation.
     *
     * Use this when launching work from contexts that carry EDT markers
     * (e.g., StartupActivity, DumbService.runWhenSmart), which would otherwise
     * cause "slow operations on EDT" violations even on background threads.
     *
     * @param block The suspend block to execute
     * @return The result of the block
     */
    fun backgroundAsync(block: suspend () -> Unit) {
        scope.launch { block() }
    }
}

/**
 * Convenience function for [IdeDispatchers.readAction].
 */
suspend fun <T> read(block: suspend () -> T): T = IdeDispatchers.readAction(block)

/**
 * Convenience function for [IdeDispatchers.writeAction].
 */
suspend fun <T> write(block: suspend () -> T): T = IdeDispatchers.writeAction(block)

/**
 * Convenience function for [IdeDispatchers.swing].
 */
suspend fun <T> swing(block: suspend () -> T): T = IdeDispatchers.swing(block)

/**
 * Convenience function for [IdeDispatchers.readSync].
 */
fun <T> readSync(block: () -> T): T = IdeDispatchers.readSync(block)

/**
 * Convenience function for [IdeDispatchers.writeSync].
 */
fun <T> writeSync(block: () -> T): T = IdeDispatchers.writeSync(block)

/**
 * Convenience function for [IdeDispatchers.swingSync].
 */
fun <T> swingSync(block: () -> T): T = IdeDispatchers.swingSync(block)

/**
 * Convenience function for [IdeDispatchers.background].
 */
suspend fun <T> background(block: suspend () -> T): T = IdeDispatchers.background(block)

/**
 * Convenience function for [IdeDispatchers.backgroundAsync].
 */
fun backgroundAsync(block: suspend () -> Unit) = IdeDispatchers.backgroundAsync(block)