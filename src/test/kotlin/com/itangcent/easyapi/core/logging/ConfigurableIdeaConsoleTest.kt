package com.itangcent.easyapi.core.logging

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class ConfigurableIdeaConsoleTest {

    private lateinit var delegate: IdeaConsole

    @Before
    fun setUp() {
        delegate = mock()
    }

    private fun consoleAt(level: Int): ConfigurableIdeaConsole =
        ConfigurableIdeaConsole(delegate, level)

    @Test
    fun testInfoAtDefaultLevel() {
        val console = consoleAt(LogLevel.INFO.threshold)
        console.info("test info")
        verify(delegate).info("test info")
    }

    @Test
    fun testInfoSuppressedAtWarnLevel() {
        val console = consoleAt(LogLevel.WARN.threshold)
        console.info("should be suppressed")
        verify(delegate, never()).info(any())
    }

    @Test
    fun testWarnAtWarnLevel() {
        val console = consoleAt(LogLevel.WARN.threshold)
        console.warn("test warning")
        verify(delegate).warn(eq("test warning"), isNull())
    }

    @Test
    fun testWarnSuppressedAtErrorLevel() {
        val console = consoleAt(LogLevel.ERROR.threshold)
        console.warn("should be suppressed")
        verify(delegate, never()).warn(any(), any())
    }

    @Test
    fun testErrorAtErrorLevel() {
        val console = consoleAt(LogLevel.ERROR.threshold)
        console.error("test error")
        verify(delegate).error(eq("test error"), isNull())
    }

    @Test
    fun testErrorNotSuppressedAtErrorLevel() {
        val console = consoleAt(LogLevel.ERROR.threshold)
        console.error("test error")
        verify(delegate).error(eq("test error"), isNull())
    }

    @Test
    fun testTraceAtTraceLevel() {
        val console = consoleAt(LogLevel.TRACE.threshold)
        console.trace("test trace")
        verify(delegate).trace("test trace")
    }

    @Test
    fun testTraceSuppressedAtInfoLevel() {
        val console = consoleAt(LogLevel.INFO.threshold)
        console.trace("should be suppressed")
        verify(delegate, never()).trace(any())
    }

    @Test
    fun testDebugAtDebugLevel() {
        val console = consoleAt(LogLevel.DEBUG.threshold)
        console.debug("test debug")
        verify(delegate).debug("test debug")
    }

    @Test
    fun testDebugSuppressedAtInfoLevel() {
        val console = consoleAt(LogLevel.INFO.threshold)
        console.debug("should be suppressed")
        verify(delegate, never()).debug(any())
    }

    @Test
    fun testAllSuppressedAtLevel100() {
        val console = consoleAt(100)
        console.trace("t")
        console.debug("d")
        console.info("i")
        console.warn("w")
        console.error("e")
        verifyNoInteractions(delegate)
    }

    @Test
    fun testWarnWithThrowable() {
        val console = consoleAt(LogLevel.WARN.threshold)
        val ex = RuntimeException("test")
        console.warn("warning", ex)
        verify(delegate).warn("warning", ex)
    }

    @Test
    fun testErrorWithThrowable() {
        val console = consoleAt(LogLevel.ERROR.threshold)
        val ex = RuntimeException("test")
        console.error("error", ex)
        verify(delegate).error("error", ex)
    }
}
