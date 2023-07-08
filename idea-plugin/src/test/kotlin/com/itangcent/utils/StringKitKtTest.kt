package com.itangcent.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StringKitTest {

    @Test
    fun testUnescape() {
        // Test unescaping an empty string
        val emptyString = "".unescape()
        assertEquals("", emptyString)

        // Test unescaping a string with no escape characters
        val noEscapeChars = "hello world".unescape()
        assertEquals("hello world", noEscapeChars)

        // Test unescaping a string with a single escape character
        val singleEscapeChar = "\\n".unescape()
        assertEquals("\n", singleEscapeChar)

        // Test unescaping a string with multiple escape characters
        val multipleEscapeChars = "first line\\nsecond line\\ttab character".unescape()
        assertEquals("first line\nsecond line\ttab character", multipleEscapeChars)

        // Test unescaping a string with an unrecognized escape character
        val unrecognizedEscapeChar = "\\x".unescape()
        assertEquals("\\x", unrecognizedEscapeChar)

        // Test unescaping a string with a trailing backslash
        val trailingBackslash = "no escape character here\\".unescape()
        assertEquals("no escape character here\\", trailingBackslash)

        // Test unescaping a string with consecutive backslashes
        val consecutiveBackslashes = "two backslashes \\\\ here".unescape()
        assertEquals("two backslashes \\ here", consecutiveBackslashes)
    }
}