package com.itangcent.easyapi.logging

import org.junit.Assert.*
import org.junit.Test

class LoggerCollectorTest {

    @Test
    fun testTrace() {
        val collector = LoggerCollector()
        collector.trace("trace message")
        
        val messages = collector.getMessages()
        assertEquals(1, messages.size)
        assertEquals(LogLevel.TRACE, messages[0].level)
        assertEquals("trace message", messages[0].message)
    }

    @Test
    fun testDebug() {
        val collector = LoggerCollector()
        collector.debug("debug message")
        
        val messages = collector.getMessages()
        assertEquals(1, messages.size)
        assertEquals(LogLevel.DEBUG, messages[0].level)
        assertEquals("debug message", messages[0].message)
    }

    @Test
    fun testInfo() {
        val collector = LoggerCollector()
        collector.info("info message")
        
        val messages = collector.getMessages()
        assertEquals(1, messages.size)
        assertEquals(LogLevel.INFO, messages[0].level)
        assertEquals("info message", messages[0].message)
    }

    @Test
    fun testWarn() {
        val collector = LoggerCollector()
        collector.warn("warning message")
        
        val messages = collector.getMessages()
        assertEquals(1, messages.size)
        assertEquals(LogLevel.WARN, messages[0].level)
        assertEquals("warning message", messages[0].message)
    }

    @Test
    fun testWarnWithThrowable() {
        val collector = LoggerCollector()
        val exception = RuntimeException("test error")
        collector.warn("warning", exception)
        
        val messages = collector.getMessages()
        assertEquals(1, messages.size)
        assertTrue(messages[0].message.contains("warning"))
        assertTrue(messages[0].message.contains("RuntimeException"))
        assertTrue(messages[0].message.contains("test error"))
    }

    @Test
    fun testError() {
        val collector = LoggerCollector()
        collector.error("error message")
        
        val messages = collector.getMessages()
        assertEquals(1, messages.size)
        assertEquals(LogLevel.ERROR, messages[0].level)
        assertEquals("error message", messages[0].message)
    }

    @Test
    fun testErrorWithThrowable() {
        val collector = LoggerCollector()
        val exception = RuntimeException("test error")
        collector.error("error", exception)
        
        val messages = collector.getMessages()
        assertEquals(1, messages.size)
        assertTrue(messages[0].message.contains("error"))
        assertTrue(messages[0].message.contains("RuntimeException"))
    }

    @Test
    fun testMultipleMessages() {
        val collector = LoggerCollector()
        collector.trace("trace")
        collector.debug("debug")
        collector.info("info")
        collector.warn("warn")
        collector.error("error")
        
        val messages = collector.getMessages()
        assertEquals(5, messages.size)
        assertEquals(LogLevel.TRACE, messages[0].level)
        assertEquals(LogLevel.DEBUG, messages[1].level)
        assertEquals(LogLevel.INFO, messages[2].level)
        assertEquals(LogLevel.WARN, messages[3].level)
        assertEquals(LogLevel.ERROR, messages[4].level)
    }

    @Test
    fun testGetMessagesReturnsCopy() {
        val collector = LoggerCollector()
        collector.info("message1")
        
        val messages1 = collector.getMessages()
        collector.info("message2")
        
        val messages2 = collector.getMessages()
        assertEquals(1, messages1.size)
        assertEquals(2, messages2.size)
    }
}
