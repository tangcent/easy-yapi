package com.itangcent.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StringKitKtTest {

    @Test
    fun unescape() {
        assertEquals("", "".unescape())
        assertEquals("abcd", "abcd".unescape())
        assertEquals("\\", "\\".unescape())
        assertEquals("\b\t\n\r\\", "\\b\\t\\n\\r\\\\".unescape())
        assertEquals("\bX\tX\nX\rX\\X\\", "\\bX\\tX\\nX\\rX\\\\X\\".unescape())
    }
}