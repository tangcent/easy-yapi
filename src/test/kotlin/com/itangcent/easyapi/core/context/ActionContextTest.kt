package com.itangcent.easyapi.core.context

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ActionContextTest {

    private lateinit var actionContext: ActionContext

    @Before
    fun setUp() {
        actionContext = ActionContext.builder()
            .bind("testValue")
            .withSpiBindings()
            .build()
    }

    @Test
    fun testBuilderCreatesContext(): Unit = runBlocking {
        assertNotNull(actionContext)
        assertFalse(actionContext.isStopped())
    }

    @Test
    fun testRunAsync(): Unit = runBlocking {
        var executed = false
        val job = actionContext.runAsync {
            delay(100)
            executed = true
        }
        job.join()
        assertTrue(executed)
    }

    @Test
    fun testStop(): Unit = runBlocking {
        assertFalse(actionContext.isStopped())
        actionContext.stop()
        assertTrue(actionContext.isStopped())
    }

    @Test
    fun testInstance(): Unit = runBlocking {
        val value: String = actionContext.instance()
        assertEquals("testValue", value)
    }

    @Test
    fun testInstanceOrNull(): Unit = runBlocking {
        val value = actionContext.instanceOrNull(String::class)
        assertEquals("testValue", value)

        val notFound = actionContext.instanceOrNull(Int::class)
        assertNull(notFound)
    }

    @Test
    fun testCheckStatus(): Unit = runBlocking {
        actionContext.checkStatus()
        actionContext.stop()
        assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            actionContext.checkStatus()
        }
    }

    @Test
    fun testRegisterAutoClear(): Unit = runBlocking {
        var cleared = false
        val autoClear = object : AutoClear {
            override suspend fun cleanup() {
                cleared = true
            }
        }
        actionContext.registerAutoClear(autoClear)
        actionContext.stop()
        assertTrue(cleared)
    }

    @Test
    fun testOnEvent(): Unit = runBlocking {
        var eventFired = false
        actionContext.on("test.event") {
            eventFired = true
        }
        assertFalse(eventFired)
    }
}
