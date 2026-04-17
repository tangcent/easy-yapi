package com.itangcent.easyapi.core.threading

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.itangcent.easyapi.logging.IdeaLog
import kotlinx.coroutines.*
import java.util.concurrent.Executors

/**
 * Provides coroutine dispatchers and threading utilities for IntelliJ Platform plugin development.
 *
 * This object centralizes all threading-related operations, ensuring proper use of
 * IntelliJ Platform's threading model. It provides:
 *
 * - **Read/Write Action Dispatchers**: For safe access to PSI and VFS operations
 * - **Swing Dispatchers**: For UI operations on the Event Dispatch Thread (EDT)
 * - **Background Dispatcher**: For compute-intensive or I/O operations
 *
 * ## Threading Model
 *
 * IntelliJ Platform requires:
 * - Read actions for PSI/VFS access (can be nested)
 * - Write actions for PSI/VFS modifications (exclusive)
 * - EDT for UI operations
 *
 * ## Usage Examples
 *
 * ```kotlin
 * // Read action for PSI access
 * readAction { psiFile.text }
 *
 * // Write action for modifications
 * writeAction { psiFile.delete() }
 *
 * // UI updates (default: nonModal - safe for general use)
 * swing { label.text = "Updated" }
 *
 * // UI updates during modal dialog
 * swing(ModalityState.any()) { dialog.updateContent() }
 *
 * // Background work
 * background { fetchDataFromServer() }
 * ```
 *
 * @see SwingDispatcher for Swing/EDT dispatcher implementation
 * @see ReadActionDispatcher for read action dispatcher implementation
 * @see WriteActionDispatcher for write action dispatcher implementation
 */
object IdeDispatchers : IdeaLog {

    /**
     * Dispatcher for read actions on IntelliJ Platform.
     *
     * Use this for:
     * - Reading PSI structure
     * - Accessing VirtualFile contents
     * - Querying project/model information
     *
     * Read actions can be nested and run concurrently with other read actions.
     *
     * @see readAction for the suspend function wrapper
     * @see readSync for synchronous execution
     */
    val ReadAction: CoroutineDispatcher = ReadActionDispatcher()

    /**
     * Dispatcher for write actions on IntelliJ Platform.
     *
     * Use this for:
     * - Modifying PSI structure
     * - Creating/deleting files
     * - Changing project settings
     *
     * Write actions are exclusive - only one can run at a time.
     * They cannot be nested with read actions.
     *
     * @see writeAction for the suspend function wrapper
     * @see writeSync for synchronous execution
     */
    val WriteAction: CoroutineDispatcher = WriteActionDispatcher()

    /**
     * Dispatcher for UI operations on the Swing Event Dispatch Thread (EDT).
     *
     * Uses [ModalityState.nonModal()] - work executes only when no modal dialogs are active.
     * This is the safest option for general UI updates and prevents "Write-unsafe context" errors.
     *
     * Use this for:
     * - General UI updates
     * - Dialog creation (non-modal)
     * - Tool window updates
     *
     * For UI updates during modal dialogs, use [SwingAny] or [swing] with [ModalityState.any()].
     *
     * @see SwingAny for modal-dialog-compatible dispatcher
     * @see swing for the suspend function wrapper
     */
    val Swing: CoroutineDispatcher = SwingDispatcher(ModalityState.nonModal())

    /**
     * Dispatcher for UI operations that must execute even during modal dialogs.
     *
     * Uses [ModalityState.any()] - work executes regardless of modal dialog state.
     *
     * Use this for:
     * - Updating UI from within a modal dialog
     * - Background tasks that need to update the UI while a dialog is open
     *
     * **Warning**: Be cautious when using this dispatcher, as it may cause issues
     * if the UI update triggers VFS operations during modal dialog transitions.
     *
     * @see Swing for the safer non-modal dispatcher
     * @see swing for the suspend function wrapper with custom modality
     */
    val SwingAny: CoroutineDispatcher = SwingDispatcher(ModalityState.any())

    private val backgroundExecutor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "EasyAPI-background").apply { isDaemon = true }
    }

    /**
     * Dispatcher for background operations that don't require read/write access or EDT.
     *
     * Uses a cached thread pool with daemon threads.
     *
     * Use this for:
     * - Network I/O
     * - File I/O (non-VFS)
     * - CPU-intensive computations
     * - Long-running tasks
     *
     * @see background for the suspend function wrapper
     * @see backgroundAsync for fire-and-forget execution
     */
    val Background: CoroutineDispatcher = PluginClassLoaderDispatcher(backgroundExecutor.asCoroutineDispatcher())

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        LOG.warn("Uncaught coroutine exception in Background", throwable)
    }

    private val scope = CoroutineScope(SupervisorJob() + Background + exceptionHandler)

    /**
     * Checks if the current thread holds a read action lock.
     */
    val isReadAccessAllowed: Boolean
        get() = ApplicationManager.getApplication().run { isReadAccessAllowed && holdsReadLock() }

    /**
     * Checks if the current thread holds a write action lock.
     */
    val isWriteAccessAllowed: Boolean
        get() = ApplicationManager.getApplication().isWriteAccessAllowed

    /**
     * Checks if the current thread is the Event Dispatch Thread (EDT).
     */
    val isDispatchThread: Boolean
        get() = ApplicationManager.getApplication().isDispatchThread

    /**
     * Returns a Swing dispatcher for the specified [ModalityState].
     *
     * This function reuses existing dispatchers when possible:
     * - [ModalityState.any()] returns [SwingAny]
     * - Other states create a new [SwingDispatcher]
     *
     * @param modalityState The modality state for the dispatcher
     * @return An appropriate [CoroutineDispatcher] for Swing operations
     */
    fun getSwingDispatcher(modalityState: ModalityState): CoroutineDispatcher {
        return when (modalityState) {
            ModalityState.nonModal() -> Swing
            ModalityState.any() -> SwingAny
            else -> SwingDispatcher(modalityState)
        }
    }

    /**
     * Executes a block within a read action.
     *
     * If already in a read action, executes immediately. Otherwise, acquires a read lock.
     *
     * @param block The code to execute with read access
     * @return The result of the block
     * @see ReadAction
     */
    suspend fun <T> readAction(block: suspend () -> T): T {
        return if (isReadAccessAllowed) {
            block()
        } else {
            withContext(ReadAction) { block() }
        }
    }

    /**
     * Executes a block within a write action.
     *
     * If already in a write action, executes immediately. Otherwise, acquires a write lock.
     *
     * @param block The code to execute with write access
     * @return The result of the block
     * @see WriteAction
     */
    suspend fun <T> writeAction(block: suspend () -> T): T {
        return if (isWriteAccessAllowed) {
            block()
        } else {
            withContext(WriteAction) { block() }
        }
    }

    /**
     * Executes a block on the Swing Event Dispatch Thread (EDT).
     *
     * Uses [ModalityState.nonModal()] - the block will only execute when no modal dialogs are active.
     * This is the safest option for general UI updates.
     *
     * If already on EDT, executes immediately.
     *
     * @param block The code to execute on EDT
     * @return The result of the block
     * @see Swing
     * @see swing(ModalityState, suspend () -> T) for modal-dialog-compatible execution
     */
    suspend fun <T> swing(block: suspend () -> T): T {
        return if (isDispatchThread) {
            block()
        } else {
            withContext(Swing) { block() }
        }
    }

    /**
     * Executes a block on the Swing Event Dispatch Thread (EDT) with a custom [ModalityState].
     *
     * Use [ModalityState.any()] to execute even when modal dialogs are active.
     * Use [ModalityState.stateForComponent] to execute for a specific dialog's modality.
     *
     * If already on EDT, executes immediately.
     *
     * @param modalityState The modality state controlling when the block executes
     * @param block The code to execute on EDT
     * @return The result of the block
     * @see SwingAny
     * @see getSwingDispatcher
     */
    suspend fun <T> swing(modalityState: ModalityState, block: suspend () -> T): T {
        return if (isDispatchThread) {
            block()
        } else {
            withContext(getSwingDispatcher(modalityState)) { block() }
        }
    }

    /**
     * Synchronously executes a block within a read action.
     *
     * @param block The code to execute with read access
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
     * Synchronously executes a block within a write action.
     *
     * @param block The code to execute with write access
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
     * Synchronously executes a block on the Swing Event Dispatch Thread (EDT).
     *
     * Blocks the current thread until execution completes.
     *
     * @param block The code to execute on EDT
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
     * Blocking version of [readAction].
     *
     * @param block The code to execute with read access
     * @return The result of the block
     */
    fun <T> readBlocking(block: suspend () -> T): T {
        return runBlocking {
            readAction(block)
        }
    }

    /**
     * Blocking version of [writeAction].
     *
     * @param block The code to execute with write access
     * @return The result of the block
     */
    fun <T> writeBlocking(block: suspend () -> T): T {
        return runBlocking {
            writeAction(block)
        }
    }

    /**
     * Blocking version of [swing].
     *
     * @param block The code to execute on EDT
     * @return The result of the block
     */
    fun <T> swingBlocking(block: suspend () -> T): T {
        return runBlocking {
            swing(block)
        }
    }

    /**
     * Launches a coroutine to execute a block within a read action.
     *
     * Fire-and-forget execution - no result is returned.
     *
     * @param block The code to execute with read access
     */
    fun readAsync(block: suspend () -> Unit) {
        scope.launch(ReadAction) { block() }
    }

    /**
     * Launches a coroutine to execute a block within a write action.
     *
     * Fire-and-forget execution - no result is returned.
     *
     * @param block The code to execute with write access
     */
    fun writeAsync(block: suspend () -> Unit) {
        scope.launch(WriteAction) { block() }
    }

    /**
     * Launches a coroutine to execute a block on the Swing EDT.
     *
     * Uses [ModalityState.nonModal()] - the block will only execute when no modal dialogs are active.
     * Fire-and-forget execution - no result is returned.
     *
     * @param block The code to execute on EDT
     * @see swingAsync(ModalityState, suspend () -> Unit) for modal-dialog-compatible execution
     */
    fun swingAsync(block: suspend () -> Unit) {
        scope.launch(Swing) { block() }
    }

    /**
     * Launches a coroutine to execute a block on the Swing EDT with a custom [ModalityState].
     *
     * Fire-and-forget execution - no result is returned.
     *
     * @param modalityState The modality state controlling when the block executes
     * @param block The code to execute on EDT
     */
    fun swingAsync(modalityState: ModalityState, block: suspend () -> Unit) {
        scope.launch(getSwingDispatcher(modalityState)) { block() }
    }

    /**
     * Executes a block on the background dispatcher.
     *
     * @param block The code to execute in background
     * @return The result of the block
     * @see Background
     */
    suspend fun <T> background(block: suspend () -> T): T = withContext(Background) { block() }

    /**
     * Launches a coroutine to execute a block on the background dispatcher.
     *
     * Fire-and-forget execution - no result is returned.
     *
     * @param block The code to execute in background
     */
    fun backgroundAsync(block: suspend () -> Unit) {
        scope.launch { block() }
    }
}

/**
 * Shorthand for [IdeDispatchers.readAction].
 */
suspend fun <T> read(block: suspend () -> T): T = IdeDispatchers.readAction(block)

/**
 * Shorthand for [IdeDispatchers.writeAction].
 */
suspend fun <T> write(block: suspend () -> T): T = IdeDispatchers.writeAction(block)

/**
 * Shorthand for [IdeDispatchers.swing].
 */
suspend fun <T> swing(block: suspend () -> T): T = IdeDispatchers.swing(block)

/**
 * Shorthand for [IdeDispatchers.swing] with custom [ModalityState].
 */
suspend fun <T> swing(modalityState: ModalityState, block: suspend () -> T): T =
    IdeDispatchers.swing(modalityState, block)

/**
 * Shorthand for [IdeDispatchers.readSync].
 */
fun <T> readSync(block: () -> T): T = IdeDispatchers.readSync(block)

/**
 * Shorthand for [IdeDispatchers.writeSync].
 */
fun <T> writeSync(block: () -> T): T = IdeDispatchers.writeSync(block)

/**
 * Shorthand for [IdeDispatchers.swingSync].
 */
fun <T> swingSync(block: () -> T): T = IdeDispatchers.swingSync(block)

/**
 * Shorthand for [IdeDispatchers.readBlocking].
 */
fun <T> readBlocking(block: suspend () -> T): T = IdeDispatchers.readBlocking(block)

/**
 * Shorthand for [IdeDispatchers.writeBlocking].
 */
fun <T> writeBlocking(block: suspend () -> T): T = IdeDispatchers.writeBlocking(block)

/**
 * Shorthand for [IdeDispatchers.swingBlocking].
 */
fun <T> swingBlocking(block: suspend () -> T): T = IdeDispatchers.swingBlocking(block)

/**
 * Shorthand for [IdeDispatchers.readAsync].
 */
fun readAsync(block: suspend () -> Unit) = IdeDispatchers.readAsync(block)

/**
 * Shorthand for [IdeDispatchers.writeAsync].
 */
fun writeAsync(block: suspend () -> Unit) = IdeDispatchers.writeAsync(block)

/**
 * Shorthand for [IdeDispatchers.swingAsync].
 */
fun swingAsync(block: suspend () -> Unit) = IdeDispatchers.swingAsync(block)

/**
 * Shorthand for [IdeDispatchers.swingAsync] with custom [ModalityState].
 */
fun swingAsync(modalityState: ModalityState, block: suspend () -> Unit) =
    IdeDispatchers.swingAsync(modalityState, block)

/**
 * Shorthand for [IdeDispatchers.background].
 */
suspend fun <T> background(block: suspend () -> T): T = IdeDispatchers.background(block)

/**
 * Shorthand for [IdeDispatchers.backgroundAsync].
 */
fun backgroundAsync(block: suspend () -> Unit) = IdeDispatchers.backgroundAsync(block)
