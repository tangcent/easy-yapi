package com.itangcent.easyapi.core.threading

import com.intellij.openapi.application.ModalityState
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

class IdeDispatchersTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testReadActionDispatcher() {
        assertNotNull(IdeDispatchers.ReadAction)
    }

    fun testSwingDispatcher() {
        assertNotNull(IdeDispatchers.Swing)
    }

    fun testSwingAnyDispatcher() {
        assertNotNull(IdeDispatchers.SwingAny)
    }

    fun testSwingDispatcherUsesNonModal() {
        val swingDispatcher = IdeDispatchers.Swing
        assertTrue("Swing dispatcher should be SwingDispatcher", swingDispatcher is SwingDispatcher)
    }

    fun testSwingAnyDispatcherUsesAnyModality() {
        val swingAnyDispatcher = IdeDispatchers.SwingAny
        assertTrue("SwingAny dispatcher should be SwingDispatcher", swingAnyDispatcher is SwingDispatcher)
    }

    fun testGetSwingDispatcherWithNonModal() {
        val dispatcher = IdeDispatchers.getSwingDispatcher(ModalityState.nonModal())
        assertSame(
            "getSwingDispatcher(nonModal()) should return Swing dispatcher",
            IdeDispatchers.Swing,
            dispatcher
        )
    }

    fun testGetSwingDispatcherWithAny() {
        val dispatcher = IdeDispatchers.getSwingDispatcher(ModalityState.any())
        assertSame(
            "getSwingDispatcher(any()) should return SwingAny dispatcher",
            IdeDispatchers.SwingAny,
            dispatcher
        )
    }

    fun testGetSwingDispatcherWithCustomModality() {
        val customModality = ModalityState.defaultModalityState()
        val dispatcher = IdeDispatchers.getSwingDispatcher(customModality)
        assertTrue(
            "getSwingDispatcher with custom modality should return SwingDispatcher",
            dispatcher is SwingDispatcher
        )
    }

    fun testBackgroundDispatcher() {
        assertNotNull(IdeDispatchers.Background)
    }

    fun testIsReadAccessAllowed() {
        val allowed = IdeDispatchers.isReadAccessAllowed
        assertNotNull(allowed)
    }

    fun testIsWriteAccessAllowed() {
        val allowed = IdeDispatchers.isWriteAccessAllowed
        assertNotNull(allowed)
    }

    fun testIsDispatchThread() {
        val isDispatchThread = IdeDispatchers.isDispatchThread
        assertNotNull(isDispatchThread)
    }

    fun testReadAction() = runBlocking {
        val result = IdeDispatchers.readSync { "test" }
        assertEquals("test", result)
    }

    fun testWriteAction() = runBlocking {
        val result = IdeDispatchers.writeAction() { "test" }
        assertEquals("test", result)
    }

    fun testSwing() = runBlocking {
        val result = IdeDispatchers.swing { "test" }
        assertEquals("test", result)
    }

    fun testReadSync() {
        val result = IdeDispatchers.readSync { "test" }
        assertEquals("test", result)
    }

    fun testWriteSync() {
        val result = IdeDispatchers.writeSync() { "test" }
        assertEquals("test", result)
    }

    fun testSwingSync() {
        val result = IdeDispatchers.swingSync { "test" }
        assertEquals("test", result)
    }

    fun testBackground() = runBlocking {
        val result = IdeDispatchers.background { "test" }
        assertEquals("test", result)
    }

    fun testBackgroundAsync() {
        var executed = false
        IdeDispatchers.backgroundAsync { executed = true }
        Thread.sleep(100)
        assertTrue(executed)
    }
}
