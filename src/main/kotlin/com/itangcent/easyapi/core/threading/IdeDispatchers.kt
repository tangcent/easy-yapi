package com.itangcent.easyapi.core.threading

import com.intellij.openapi.application.ApplicationManager
import com.itangcent.easyapi.logging.IdeaLog
import kotlinx.coroutines.*

object IdeDispatchers : IdeaLog {
    val ReadAction: CoroutineDispatcher = ReadActionDispatcher()
    val WriteAction: CoroutineDispatcher = WriteActionDispatcher()
    val Swing: CoroutineDispatcher = SwingDispatcher()

    private val backgroundExecutor = java.util.concurrent.Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "EasyAPI-background").apply { isDaemon = true }
    }

    val Background: CoroutineDispatcher = PluginClassLoaderDispatcher(backgroundExecutor.asCoroutineDispatcher())

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        LOG.warn("Uncaught coroutine exception in Background", throwable)
    }

    private val scope = CoroutineScope(SupervisorJob() + Background + exceptionHandler)

    val isReadAccessAllowed: Boolean
        get() = ApplicationManager.getApplication().isReadAccessAllowed

    val isWriteAccessAllowed: Boolean
        get() = ApplicationManager.getApplication().isWriteAccessAllowed

    val isDispatchThread: Boolean
        get() = ApplicationManager.getApplication().isDispatchThread

    suspend fun <T> readAction(block: suspend () -> T): T {
        return if (isReadAccessAllowed) {
            block()
        } else {
            withContext(ReadAction) { block() }
        }
    }

    suspend fun <T> writeAction(block: suspend () -> T): T {
        return if (isWriteAccessAllowed) {
            block()
        } else {
            withContext(WriteAction) { block() }
        }
    }

    suspend fun <T> swing(block: suspend () -> T): T {
        return if (isDispatchThread) {
            block()
        } else {
            withContext(Swing) { block() }
        }
    }

    fun <T> readSync(block: () -> T): T {
        return if (isReadAccessAllowed) {
            block()
        } else {
            ApplicationManager.getApplication().runReadAction<T>(block)
        }
    }

    fun <T> writeSync(block: () -> T): T {
        return if (isWriteAccessAllowed) {
            block()
        } else {
            ApplicationManager.getApplication().runWriteAction<T>(block)
        }
    }

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

    fun readAsync(block: suspend () -> Unit) {
        scope.launch(ReadAction) { block() }
    }

    fun writeAsync(block: suspend () -> Unit) {
        scope.launch(WriteAction) { block() }
    }

    fun swingAsync(block: suspend () -> Unit) {
        scope.launch(Swing) { block() }
    }

    suspend fun <T> background(block: suspend () -> T): T = withContext(Background) { block() }

    fun backgroundAsync(block: suspend () -> Unit) {
        scope.launch { block() }
    }
}

suspend fun <T> read(block: suspend () -> T): T = IdeDispatchers.readAction(block)

suspend fun <T> write(block: suspend () -> T): T = IdeDispatchers.writeAction(block)

suspend fun <T> swing(block: suspend () -> T): T = IdeDispatchers.swing(block)

fun <T> readSync(block: () -> T): T = IdeDispatchers.readSync(block)

fun <T> writeSync(block: () -> T): T = IdeDispatchers.writeSync(block)

fun <T> swingSync(block: () -> T): T = IdeDispatchers.swingSync(block)

fun readAsync(block: suspend () -> Unit) = IdeDispatchers.readAsync(block)

fun writeAsync(block: suspend () -> Unit) = IdeDispatchers.writeAsync(block)

fun swingAsync(block: suspend () -> Unit) = IdeDispatchers.swingAsync(block)

suspend fun <T> background(block: suspend () -> T): T = IdeDispatchers.background(block)

fun backgroundAsync(block: suspend () -> Unit) = IdeDispatchers.backgroundAsync(block)
