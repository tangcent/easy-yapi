package com.itangcent.easyapi.core.context

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
    fun testBuilderCreatesContext() {
        assertNotNull(actionContext)
        assertFalse(actionContext.isStopped())
    }

    @Test
    fun testStop() {
        assertFalse(actionContext.isStopped())
        actionContext.stop()
        assertTrue(actionContext.isStopped())
    }

    @Test
    fun testInstance() {
        val value: String = actionContext.instance()
        assertEquals("testValue", value)
    }

    @Test
    fun testInstanceOrNull() {
        val value = actionContext.instanceOrNull(String::class)
        assertEquals("testValue", value)

        val notFound = actionContext.instanceOrNull(Int::class)
        assertNull(notFound)
    }

    @Test
    fun testCheckStatus() {
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
    fun testOnEvent() {
        var eventFired = false
        actionContext.on("test.event") {
            eventFired = true
        }
        assertFalse(eventFired)
    }
}
