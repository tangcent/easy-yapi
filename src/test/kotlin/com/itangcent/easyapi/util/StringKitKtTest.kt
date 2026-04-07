package com.itangcent.easyapi.util

import org.junit.Assert.*
import org.junit.Test

class StringKitKtTest {

    @Test
    fun testAppend_bothNonBlank() {
        assertEquals("hello\nworld", "hello".append("world"))
    }

    @Test
    fun testAppend_customSeparator() {
        assertEquals("hello world", "hello".append("world", " "))
        assertEquals("hello,world", "hello".append("world", ","))
    }

    @Test
    fun testAppend_thisNull() {
        assertEquals("world", null.append("world"))
    }

    @Test
    fun testAppend_thisBlank() {
        assertEquals("world", "".append("world"))
        assertEquals("world", "   ".append("world"))
    }

    @Test
    fun testAppend_otherNull() {
        assertEquals("hello", "hello".append(null))
    }

    @Test
    fun testAppend_otherBlank() {
        assertEquals("hello", "hello".append(""))
        assertEquals("hello", "hello".append("   "))
    }

    @Test
    fun testAppend_bothNull() {
        assertEquals("", null.append(null))
    }

    @Test
    fun testAppend_bothBlank() {
        // When both are blank, returns "this" (the first blank string)
        assertEquals("", "".append(""))
        assertEquals("   ", "   ".append("   "))
    }

    @Test
    fun testAppend_multiLine() {
        val result = "line1".append("line2").append("line3")
        assertEquals("line1\nline2\nline3", result)
    }
}
