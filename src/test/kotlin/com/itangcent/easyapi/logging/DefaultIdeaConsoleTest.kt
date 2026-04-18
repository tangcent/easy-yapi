package com.itangcent.easyapi.logging

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class DefaultIdeaConsoleTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var console: DefaultIdeaConsole

    override fun setUp() {
        super.setUp()
        console = DefaultIdeaConsole(project)
    }

    fun testInfoDoesNotThrow() {
        console.info("test info message")
    }

    fun testWarnDoesNotThrow() {
        console.warn("test warning message")
    }

    fun testErrorDoesNotThrow() {
        console.error("test error message")
    }

    fun testTraceDoesNotThrow() {
        console.trace("test trace message")
    }

    fun testDebugDoesNotThrow() {
        console.debug("test debug message")
    }

    fun testPrintlnDoesNotThrow() {
        console.println("test println message")
    }

    fun testPrintDoesNotThrow() {
        console.print("test print message")
    }

    fun testWarnWithThrowableDoesNotThrow() {
        console.warn("warning with exception", RuntimeException("test"))
    }

    fun testErrorWithThrowableDoesNotThrow() {
        console.error("error with exception", RuntimeException("test"))
    }

    fun testWindowId() {
        assertEquals("EasyAPI", DefaultIdeaConsole.WINDOW_ID)
    }
}
