package com.itangcent.easyapi.logging

import org.junit.Assert.*
import org.junit.Test

class LogMessageTest {

    @Test
    fun testLogMessageCreation() {
        val timestamp = System.currentTimeMillis()
        val logMessage = LogMessage(
            level = LogLevel.INFO,
            message = "Test message",
            timestamp = timestamp
        )
        assertEquals(LogLevel.INFO, logMessage.level)
        assertEquals("Test message", logMessage.message)
        assertEquals(timestamp, logMessage.timestamp)
    }

    @Test
    fun testLogMessageWithDefaultTimestamp() {
        val before = System.currentTimeMillis()
        val logMessage = LogMessage(LogLevel.DEBUG, "Debug message")
        val after = System.currentTimeMillis()
        
        assertTrue(logMessage.timestamp >= before)
        assertTrue(logMessage.timestamp <= after)
    }

    @Test
    fun testLogMessageEquality() {
        val timestamp = 1234567890L
        val msg1 = LogMessage(LogLevel.INFO, "Test", timestamp)
        val msg2 = LogMessage(LogLevel.INFO, "Test", timestamp)
        assertEquals(msg1, msg2)
    }

    @Test
    fun testLogMessageCopy() {
        val original = LogMessage(LogLevel.INFO, "Original")
        val copy = original.copy(message = "Modified")
        assertEquals("Modified", copy.message)
        assertEquals("Original", original.message)
    }

    @Test
    fun testLogMessageComponentFunctions() {
        val timestamp = 1234567890L
        val logMessage = LogMessage(LogLevel.WARN, "Warning", timestamp)
        val (level, message, ts) = logMessage
        assertEquals(LogLevel.WARN, level)
        assertEquals("Warning", message)
        assertEquals(timestamp, ts)
    }
}

class LogLevelTest {

    @Test
    fun testLogLevelThresholds() {
        assertEquals(0, LogLevel.TRACE.threshold)
        assertEquals(10, LogLevel.DEBUG.threshold)
        assertEquals(20, LogLevel.INFO.threshold)
        assertEquals(30, LogLevel.WARN.threshold)
        assertEquals(40, LogLevel.ERROR.threshold)
    }

    @Test
    fun testLogLevelOrdering() {
        assertTrue(LogLevel.TRACE.threshold < LogLevel.DEBUG.threshold)
        assertTrue(LogLevel.DEBUG.threshold < LogLevel.INFO.threshold)
        assertTrue(LogLevel.INFO.threshold < LogLevel.WARN.threshold)
        assertTrue(LogLevel.WARN.threshold < LogLevel.ERROR.threshold)
    }

    @Test
    fun testLogLevelNames() {
        assertEquals("TRACE", LogLevel.TRACE.name)
        assertEquals("DEBUG", LogLevel.DEBUG.name)
        assertEquals("INFO", LogLevel.INFO.name)
        assertEquals("WARN", LogLevel.WARN.name)
        assertEquals("ERROR", LogLevel.ERROR.name)
    }

    @Test
    fun testLogLevelValues() {
        val levels = LogLevel.entries
        assertEquals(5, levels.size)
        assertTrue(levels.contains(LogLevel.TRACE))
        assertTrue(levels.contains(LogLevel.DEBUG))
        assertTrue(levels.contains(LogLevel.INFO))
        assertTrue(levels.contains(LogLevel.WARN))
        assertTrue(levels.contains(LogLevel.ERROR))
    }
}
