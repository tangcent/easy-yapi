package com.itangcent.intellij.util

import com.itangcent.mock.BaseContextTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Test case of [FileType]
 */
internal class FileTypeTest : BaseContextTest() {

    @Test
    fun suffix() {
        assertTrue(FileType.acceptable("xxxx.java"))
        assertTrue(FileType.acceptable("xxxx.kt"))
        assertFalse(FileType.acceptable("xxxx.scala"))
    }
}