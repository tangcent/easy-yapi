package com.itangcent.easyapi.core.threading

import com.intellij.openapi.application.ApplicationManager
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.*

class ReadActionDispatcherTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var dispatcher: ReadActionDispatcher

    override fun setUp() {
        super.setUp()
        dispatcher = ReadActionDispatcher()
    }

    fun testDispatchExecutesBlock() = runBlocking {
        var executed = false
        val block = Runnable { executed = true }

        dispatcher.dispatch(coroutineContext, block)

        // The block may be dispatched to IO, give it time to execute
        Thread.sleep(200)
        assertTrue("Block should have been executed", executed)
    }

    fun testDispatchRunsUnderReadAccess() = runBlocking {
        val result = CompletableDeferred<Boolean>()

        dispatcher.dispatch(coroutineContext, Runnable {
            result.complete(ApplicationManager.getApplication().isReadAccessAllowed)
        })

        assertTrue("Block should run under read access", result.await())
    }

    fun testDispatchImmediatelyWhenAlreadyHasReadAccess() = runBlocking {
        // When already inside a read action, dispatch should run immediately (synchronously)
        ApplicationManager.getApplication().runReadAction {
            var executed = false
            val block = Runnable { executed = true }

            runBlocking {
                dispatcher.dispatch(coroutineContext, block)
            }

            assertTrue("Block should have been executed immediately", executed)
        }
    }

    fun testWithContextReadAction() = runBlocking {
        val result = withContext(dispatcher) {
            "hello from read action"
        }
        assertEquals("hello from read action", result)
    }

    fun testWithContextReadActionHasReadAccess() = runBlocking {
        val hasAccess = withContext(dispatcher) {
            ApplicationManager.getApplication().isReadAccessAllowed
        }
        assertTrue("Should have read access inside dispatcher", hasAccess)
    }

    fun testCancelledCoroutineDoesNotThrowUnhandledException() {
        // Verify that cancelling a coroutine while it's dispatching to ReadAction
        // does not produce an unhandled JobCancellationException
        val job = Job()
        val scope = CoroutineScope(Dispatchers.Default + job)

        val started = CompletableDeferred<Unit>()

        scope.launch {
            withContext(dispatcher) {
                started.complete(Unit)
                // Simulate some work
                Thread.sleep(50)
            }
        }

        runBlocking {
            started.await()
        }

        // Cancel the scope — this should not cause an unhandled exception
        scope.cancel()

        // Give time for any exception to propagate
        Thread.sleep(200)

        // If we get here without an unhandled exception, the test passes
    }
}
