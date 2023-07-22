package com.itangcent.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

/**
 * Test case for [FileKit]
 */
class FileKitKtTest {
    @Test
    @EnabledOnOs(OS.WINDOWS, disabledReason = "Only for Windows")
    fun testLocalPath_windows() {
        val input = "/foo/bar"
        val expected = "\\foo\\bar"
        assertEquals(expected, input.localPath())
    }

    @Test
    @EnabledOnOs(value = [OS.LINUX, OS.MAC], disabledReason = "Only for Linux and Mac")
    fun testLocalPath_linux() {
        val input = "/foo/bar"
        assertEquals(input, input.localPath())
    }
}