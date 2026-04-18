package com.itangcent.easyapi.logging

import org.junit.Assert.*
import org.junit.Test

class IdeaLogConsoleTest {

    @Test
    fun testTraceDoesNotThrow() {
        IdeaLogConsole.trace("test trace")
    }

    @Test
    fun testDebugDoesNotThrow() {
        IdeaLogConsole.debug("test debug")
    }

    @Test
    fun testInfoDoesNotThrow() {
        IdeaLogConsole.info("test info")
    }

    @Test
    fun testWarnDoesNotThrow() {
        IdeaLogConsole.warn("test warning")
    }

    @Test
    fun testWarnWithThrowableDoesNotThrow() {
        IdeaLogConsole.warn("test warning", RuntimeException("test"))
    }

    @Test
    fun testImplementsIdeaConsole() {
        assertTrue("IdeaLogConsole should implement IdeaConsole", IdeaLogConsole is IdeaConsole)
    }

    @Test
    fun testImplementsIdeaLog() {
        assertTrue("IdeaLogConsole should implement IdeaLog", IdeaLogConsole is IdeaLog)
    }

    @Test
    fun testIsSingletonObject() {
        assertSame("IdeaLogConsole should be singleton", IdeaLogConsole, IdeaLogConsole)
    }

    @Test
    fun testErrorMethodExists() {
        val method = IdeaLogConsole::class.java.getMethod("error", String::class.java, Throwable::class.java)
        assertNotNull("error method should exist", method)
    }
}
