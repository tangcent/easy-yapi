package com.itangcent.easyapi.core.threading

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.runBlocking

class SwingDispatcherTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var swingDispatcher: SwingDispatcher

    override fun setUp() {
        super.setUp()
        swingDispatcher = SwingDispatcher()
    }

    fun testDispatchBlock() = runBlocking {
        var executed = false
        val block = Runnable { executed = true }
        
        swingDispatcher.dispatch(coroutineContext, block)
        
        assertTrue(executed)
    }

    fun testDispatchMultipleBlocks() = runBlocking {
        var count = 0
        val block1 = Runnable { count++ }
        val block2 = Runnable { count++ }
        val block3 = Runnable { count++ }
        
        swingDispatcher.dispatch(coroutineContext, block1)
        swingDispatcher.dispatch(coroutineContext, block2)
        swingDispatcher.dispatch(coroutineContext, block3)
        
        assertEquals(3, count)
    }
}
