package com.itangcent.easyapi.logging

import com.itangcent.easyapi.settings.Settings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class ConfigurableIdeaConsoleTest {

    private lateinit var delegate: IdeaConsole
    private lateinit var settings: Settings
    private lateinit var console: ConfigurableIdeaConsole

    @Before
    fun setUp() {
        delegate = mock()
        settings = Settings()
        console = ConfigurableIdeaConsole(delegate, settings)
    }

    @Test
    fun testInfoAtDefaultLevel() {
        settings.logLevel = LogLevel.INFO.threshold
        console.info("test info")
        verify(delegate).info("test info")
    }

    @Test
    fun testInfoSuppressedAtWarnLevel() {
        settings.logLevel = LogLevel.WARN.threshold
        console.info("should be suppressed")
        verify(delegate, never()).info(any())
    }

    @Test
    fun testWarnAtWarnLevel() {
        settings.logLevel = LogLevel.WARN.threshold
        console.warn("test warning")
        verify(delegate).warn(eq("test warning"), isNull())
    }

    @Test
    fun testWarnSuppressedAtErrorLevel() {
        settings.logLevel = LogLevel.ERROR.threshold
        console.warn("should be suppressed")
        verify(delegate, never()).warn(any(), any())
    }

    @Test
    fun testErrorAtErrorLevel() {
        settings.logLevel = LogLevel.ERROR.threshold
        console.error("test error")
        verify(delegate).error(eq("test error"), isNull())
    }

    @Test
    fun testErrorNotSuppressedAtErrorLevel() {
        settings.logLevel = LogLevel.ERROR.threshold
        console.error("test error")
        verify(delegate).error(eq("test error"), isNull())
    }

    @Test
    fun testTraceAtTraceLevel() {
        settings.logLevel = LogLevel.TRACE.threshold
        console.trace("test trace")
        verify(delegate).trace("test trace")
    }

    @Test
    fun testTraceSuppressedAtInfoLevel() {
        settings.logLevel = LogLevel.INFO.threshold
        console.trace("should be suppressed")
        verify(delegate, never()).trace(any())
    }

    @Test
    fun testDebugAtDebugLevel() {
        settings.logLevel = LogLevel.DEBUG.threshold
        console.debug("test debug")
        verify(delegate).debug("test debug")
    }

    @Test
    fun testDebugSuppressedAtInfoLevel() {
        settings.logLevel = LogLevel.INFO.threshold
        console.debug("should be suppressed")
        verify(delegate, never()).debug(any())
    }

    @Test
    fun testAllSuppressedAtLevel100() {
        settings.logLevel = 100
        console.trace("t")
        console.debug("d")
        console.info("i")
        console.warn("w")
        console.error("e")
        verifyNoInteractions(delegate)
    }

    @Test
    fun testWarnWithThrowable() {
        settings.logLevel = LogLevel.WARN.threshold
        val ex = RuntimeException("test")
        console.warn("warning", ex)
        verify(delegate).warn("warning", ex)
    }

    @Test
    fun testErrorWithThrowable() {
        settings.logLevel = LogLevel.ERROR.threshold
        val ex = RuntimeException("test")
        console.error("error", ex)
        verify(delegate).error("error", ex)
    }
}
