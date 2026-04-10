package com.itangcent.easyapi.core.event

import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.logging.IdeaLogConsole
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class CoroutineEventBusTest {

    private lateinit var eventBus: CoroutineEventBus

    @Before
    fun setUp() {
        eventBus = CoroutineEventBus(IdeaLogConsole)
    }

    @Test
    fun testRegisterAndFire(): Unit = runBlocking {
        var fired = false
        eventBus.register("TEST_EVENT") { _ ->
            fired = true
        }

        val context = ActionContext.builder().build()
        eventBus.fire("TEST_EVENT", context)

        assertTrue(fired)
    }

    @Test
    fun testMultipleHandlers(): Unit = runBlocking {
        val results = mutableListOf<String>()
        eventBus.register("MULTI_EVENT") { _ -> results.add("handler1") }
        eventBus.register("MULTI_EVENT") { _ -> results.add("handler2") }
        eventBus.register("MULTI_EVENT") { _ -> results.add("handler3") }

        val context = ActionContext.builder().build()
        eventBus.fire("MULTI_EVENT", context)

        assertEquals(3, results.size)
        assertEquals(listOf("handler1", "handler2", "handler3"), results)
    }

    @Test
    fun testFireNonExistentKey(): Unit = runBlocking {
        val context = ActionContext.builder().build()
        eventBus.fire("NON_EXISTENT", context)
    }

    @Test
    fun testHandlerReceivesContext(): Unit = runBlocking {
        var receivedContext: ActionContext? = null
        eventBus.register("CONTEXT_EVENT") { ctx ->
            receivedContext = ctx
        }

        val context = ActionContext.builder().bind("testValue").build()
        eventBus.fire("CONTEXT_EVENT", context)

        assertNotNull(receivedContext)
        assertEquals("testValue", receivedContext?.instanceOrNull(String::class))
    }

    @Test
    fun testHandlerExceptionDoesNotStopOtherHandlers(): Unit = runBlocking {
        val results = mutableListOf<String>()
        eventBus.register("ERROR_EVENT") { _ -> results.add("before") }
        eventBus.register("ERROR_EVENT") { _ -> throw RuntimeException("Test error") }
        eventBus.register("ERROR_EVENT") { _ -> results.add("after") }

        val context = ActionContext.builder().build()
        eventBus.fire("ERROR_EVENT", context)

        assertEquals(listOf("before", "after"), results)
    }

    @Test
    fun testDifferentEventKeys(): Unit = runBlocking {
        val results = mutableListOf<String>()
        eventBus.register("EVENT_A") { _ -> results.add("A") }
        eventBus.register("EVENT_B") { _ -> results.add("B") }

        val context = ActionContext.builder().build()
        eventBus.fire("EVENT_A", context)

        assertEquals(listOf("A"), results)

        eventBus.fire("EVENT_B", context)
        assertEquals(listOf("A", "B"), results)
    }

    @Test
    fun testSuspendHandler(): Unit = runBlocking {
        var completed = false
        eventBus.register("SUSPEND_EVENT") { _ ->
            delay(10.milliseconds)
            completed = true
        }

        val context = ActionContext.builder().build()
        eventBus.fire("SUSPEND_EVENT", context)

        assertTrue(completed)
    }
}
