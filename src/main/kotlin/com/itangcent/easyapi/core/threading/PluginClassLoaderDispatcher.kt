package com.itangcent.easyapi.core.threading

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/**
 * Wraps a delegate [CoroutineDispatcher] and ensures the thread context classloader
 * is set to the plugin's classloader before running each dispatched block.
 *
 * This prevents [java.util.ServiceLoader] failures on `Dispatchers.Default` worker threads,
 * where the context classloader cannot see IntelliJ platform SPI entries
 * (e.g. `CoroutineExceptionHandlerImpl`).
 */
class PluginClassLoaderDispatcher(
    private val delegate: CoroutineDispatcher
) : CoroutineDispatcher() {

    private val pluginClassLoader: ClassLoader = PluginClassLoaderDispatcher::class.java.classLoader

    /**
     * Dispatches the block to the delegate dispatcher with the plugin's classloader set as context.
     *
     * @param context The coroutine context
     * @param block The block to execute
     */
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        delegate.dispatch(context, Runnable {
            val original = Thread.currentThread().contextClassLoader
            Thread.currentThread().contextClassLoader = pluginClassLoader
            try {
                block.run()
            } finally {
                Thread.currentThread().contextClassLoader = original
            }
        })
    }
}
