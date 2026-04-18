package com.itangcent.easyapi.core.threading

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class PluginClassLoaderDispatcherTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var pluginClassLoaderDispatcher: PluginClassLoaderDispatcher

    override fun setUp() {
        super.setUp()
        pluginClassLoaderDispatcher = PluginClassLoaderDispatcher(kotlinx.coroutines.Dispatchers.Default)
    }

    fun testDispatchSetsPluginClassLoader() = runBlocking {
        val latch = CountDownLatch(1)
        val capturedClassLoaders = mutableListOf<ClassLoader>()

        pluginClassLoaderDispatcher.dispatch(coroutineContext, Runnable {
            capturedClassLoaders.add(Thread.currentThread().contextClassLoader)
            latch.countDown()
        })

        assertTrue("Block should have been executed", latch.await(2, TimeUnit.SECONDS))
        assertEquals(
            "Context classloader should be the plugin classloader",
            PluginClassLoaderDispatcher::class.java.classLoader,
            capturedClassLoaders.first()
        )
    }

    fun testDispatchRestoresOriginalClassLoader() = runBlocking {
        val originalClassLoader = Thread.currentThread().contextClassLoader
        val latch = CountDownLatch(1)

        pluginClassLoaderDispatcher.dispatch(coroutineContext, Runnable {
            latch.countDown()
        })

        assertTrue("Block should have completed", latch.await(2, TimeUnit.SECONDS))
        assertEquals(
            "Original classloader should be restored after dispatch",
            originalClassLoader,
            Thread.currentThread().contextClassLoader
        )
    }

    fun testWithContextUsesPluginClassLoader() = runBlocking {
        val classLoader = withContext(pluginClassLoaderDispatcher) {
            Thread.currentThread().contextClassLoader
        }
        assertEquals(
            "withContext should use plugin classloader",
            PluginClassLoaderDispatcher::class.java.classLoader,
            classLoader
        )
    }

    fun testDispatchExecutesBlock() = runBlocking {
        val latch = CountDownLatch(1)
        pluginClassLoaderDispatcher.dispatch(coroutineContext, Runnable { latch.countDown() })
        assertTrue("Block should have been executed", latch.await(2, TimeUnit.SECONDS))
    }

    fun testDispatchMultipleBlocks() = runBlocking {
        val latch = CountDownLatch(3)
        repeat(3) {
            pluginClassLoaderDispatcher.dispatch(coroutineContext, Runnable { latch.countDown() })
        }
        assertTrue("All blocks should have been executed", latch.await(3, TimeUnit.SECONDS))
    }

    fun testDelegateDispatcherIsWrapped() {
        val delegate = kotlinx.coroutines.Dispatchers.Default
        val dispatcher = PluginClassLoaderDispatcher(delegate)
        assertNotNull("PluginClassLoaderDispatcher should be created", dispatcher)
        assertTrue(
            "PluginClassLoaderDispatcher should be a CoroutineDispatcher",
            dispatcher is CoroutineDispatcher
        )
    }
}
