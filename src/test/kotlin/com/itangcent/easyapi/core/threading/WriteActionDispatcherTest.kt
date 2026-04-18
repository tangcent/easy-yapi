package com.itangcent.easyapi.core.threading

import com.intellij.openapi.application.ApplicationManager
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class WriteActionDispatcherTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var writeActionDispatcher: WriteActionDispatcher

    override fun setUp() {
        super.setUp()
        writeActionDispatcher = WriteActionDispatcher()
    }

    fun testDispatchExecutesBlock() = runBlocking {
        var executed = false
        writeActionDispatcher.dispatch(coroutineContext, Runnable { executed = true })
        assertTrue("Block should have been executed", executed)
    }

    fun testDispatchRunsUnderWriteAccess() = runBlocking {
        var hasWriteAccess = false
        writeActionDispatcher.dispatch(coroutineContext, Runnable {
            hasWriteAccess = ApplicationManager.getApplication().isWriteAccessAllowed
        })
        assertTrue("Block should run under write access", hasWriteAccess)
    }

    fun testIsDispatchNeededReturnsTrue() {
        assertTrue("isDispatchNeeded should always return true", writeActionDispatcher.isDispatchNeeded(EmptyCoroutineContext))
    }

    fun testWithContextWriteAction() = runBlocking {
        val result = withContext(writeActionDispatcher) {
            "hello from write action"
        }
        assertEquals("hello from write action", result)
    }

    fun testWithContextWriteActionHasWriteAccess() = runBlocking {
        val hasAccess = withContext(writeActionDispatcher) {
            ApplicationManager.getApplication().isWriteAccessAllowed
        }
        assertTrue("Should have write access inside dispatcher", hasAccess)
    }

    fun testDispatchMultipleBlocks() = runBlocking {
        var count = 0
        repeat(3) {
            writeActionDispatcher.dispatch(coroutineContext, Runnable { count++ })
        }
        assertEquals("All blocks should have been executed", 3, count)
    }
}
