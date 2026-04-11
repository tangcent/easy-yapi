package com.itangcent.easyapi.core.threading

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

class IdeDispatchersTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testReadActionDispatcher() {
        assertNotNull(IdeDispatchers.ReadAction)
    }

    fun testWriteActionDispatcher() {
        assertNotNull(IdeDispatchers.WriteAction)
    }

    fun testSwingDispatcher() {
        assertNotNull(IdeDispatchers.Swing)
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
        val result = IdeDispatchers.writeAction { "test" }
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
        val result = IdeDispatchers.writeSync { "test" }
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
