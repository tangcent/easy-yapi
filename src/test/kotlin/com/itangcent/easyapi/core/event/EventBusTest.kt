package com.itangcent.easyapi.core.event

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class EventBusTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var eventBus: EventBus

    override fun setUp() {
        super.setUp()
        eventBus = EventBus.getInstance(project)
    }

    fun testGetInstanceReturnsNonNull() {
        assertNotNull("EventBus instance should not be null", eventBus)
    }

    fun testGetInstanceReturnsSameInstance() {
        val instance1 = EventBus.getInstance(project)
        val instance2 = EventBus.getInstance(project)
        assertSame("EventBus should be a project-level singleton", instance1, instance2)
    }

    fun testRegisterHandlerIsCalledOnActionCompleted() {
        val latch = CountDownLatch(1)
        val called = AtomicInteger(0)

        eventBus.register(EventKeys.ON_COMPLETED) {
            called.incrementAndGet()
            latch.countDown()
        }

        project.messageBus.syncPublisher(ActionCompletedTopic.TOPIC).onActionCompleted()

        assertTrue("Handler should be called within 5 seconds",
            latch.await(5, TimeUnit.SECONDS))
        assertEquals("Handler should be called exactly once", 1, called.get())
    }

    fun testRegisterMultipleHandlersForSameKey() {
        val latch = CountDownLatch(2)
        val callOrder = mutableListOf<Int>()

        eventBus.register(EventKeys.ON_COMPLETED) {
            synchronized(callOrder) { callOrder.add(1) }
            latch.countDown()
        }
        eventBus.register(EventKeys.ON_COMPLETED) {
            synchronized(callOrder) { callOrder.add(2) }
            latch.countDown()
        }

        project.messageBus.syncPublisher(ActionCompletedTopic.TOPIC).onActionCompleted()

        assertTrue("Both handlers should be called within 5 seconds",
            latch.await(5, TimeUnit.SECONDS))
        assertEquals("Both handlers should be called", 2, callOrder.size)
    }

    fun testPersistentHandlerIsCalledOnEachFire() {
        val latch1 = CountDownLatch(1)
        val latch2 = CountDownLatch(2)
        val callCount = AtomicInteger(0)

        eventBus.register(EventKeys.ON_COMPLETED) {
            callCount.incrementAndGet()
            latch1.countDown()
            latch2.countDown()
        }

        project.messageBus.syncPublisher(ActionCompletedTopic.TOPIC).onActionCompleted()
        assertTrue("First call should complete within 5 seconds",
            latch1.await(5, TimeUnit.SECONDS))

        project.messageBus.syncPublisher(ActionCompletedTopic.TOPIC).onActionCompleted()
        assertTrue("Second call should complete within 5 seconds",
            latch2.await(5, TimeUnit.SECONDS))

        assertEquals("Persistent handler should be called twice", 2, callCount.get())
    }

    fun testRegisterOnceHandlerIsCalledOnlyOnce() {
        val latch = CountDownLatch(1)
        val callCount = AtomicInteger(0)

        eventBus.registerOnce(EventKeys.ON_COMPLETED) {
            callCount.incrementAndGet()
            latch.countDown()
        }

        project.messageBus.syncPublisher(ActionCompletedTopic.TOPIC).onActionCompleted()
        assertTrue("First call should complete within 5 seconds",
            latch.await(5, TimeUnit.SECONDS))

        Thread.sleep(200)

        assertEquals("One-time handler should be called exactly once", 1, callCount.get())
    }

    fun testRegisterOnceHandlerIsNotCalledOnSecondFire() {
        val latch = CountDownLatch(1)
        val callCount = AtomicInteger(0)

        eventBus.registerOnce(EventKeys.ON_COMPLETED) {
            callCount.incrementAndGet()
            latch.countDown()
        }

        project.messageBus.syncPublisher(ActionCompletedTopic.TOPIC).onActionCompleted()
        assertTrue("First call should complete within 5 seconds",
            latch.await(5, TimeUnit.SECONDS))

        project.messageBus.syncPublisher(ActionCompletedTopic.TOPIC).onActionCompleted()

        Thread.sleep(200)

        assertEquals("One-time handler should not be called again", 1, callCount.get())
    }

    fun testMixOfPersistentAndOneTimeHandlers() {
        val latch1 = CountDownLatch(2)
        val persistentCount = AtomicInteger(0)
        val oneTimeCount = AtomicInteger(0)

        eventBus.register(EventKeys.ON_COMPLETED) {
            persistentCount.incrementAndGet()
            latch1.countDown()
        }
        eventBus.registerOnce(EventKeys.ON_COMPLETED) {
            oneTimeCount.incrementAndGet()
            latch1.countDown()
        }

        project.messageBus.syncPublisher(ActionCompletedTopic.TOPIC).onActionCompleted()
        assertTrue("First fire should complete within 5 seconds",
            latch1.await(5, TimeUnit.SECONDS))

        assertEquals("After first fire: persistent=1, oneTime=1", 1, persistentCount.get())
        assertEquals("After first fire: oneTime=1", 1, oneTimeCount.get())

        val latch2 = CountDownLatch(1)
        eventBus.register(EventKeys.ON_COMPLETED) {
            latch2.countDown()
        }

        project.messageBus.syncPublisher(ActionCompletedTopic.TOPIC).onActionCompleted()
        assertTrue("Second fire should complete within 5 seconds",
            latch2.await(5, TimeUnit.SECONDS))

        Thread.sleep(200)

        assertEquals("Persistent handler should be called twice (once per fire)", 2, persistentCount.get())
        assertEquals("One-time handler should be called once total", 1, oneTimeCount.get())
    }

    fun testHandlerExceptionDoesNotPreventOtherHandlers() {
        val latch = CountDownLatch(1)
        val secondCalled = AtomicInteger(0)

        eventBus.register(EventKeys.ON_COMPLETED) {
            throw RuntimeException("Test exception")
        }
        eventBus.register(EventKeys.ON_COMPLETED) {
            secondCalled.incrementAndGet()
            latch.countDown()
        }

        project.messageBus.syncPublisher(ActionCompletedTopic.TOPIC).onActionCompleted()

        assertTrue("Second handler should still be called within 5 seconds",
            latch.await(5, TimeUnit.SECONDS))
        assertEquals("Second handler should be called despite first handler exception",
            1, secondCalled.get())
    }

    fun testRegisterForUnknownKeyDoesNotCrash() {
        eventBus.register("UNKNOWN_KEY") {
        }
        eventBus.registerOnce("ANOTHER_UNKNOWN_KEY") {
        }
    }
}
