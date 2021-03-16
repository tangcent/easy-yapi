package com.itangcent.common.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.math.BigInteger
import java.nio.file.Path


/**
 * Test case of [FileSizeUtils]
 */
class FileSizeUtilsTest {

    private var tempDir: Path? = null
    private val separator = File.separator

    /**
     * build dir and files:
     * temp--A--a--1.txt
     *      ├B--b--2.java
     *      └C--c--3.kt
     *         └d--3.txt
     *            └4.txt
     */
    @BeforeEach
    fun before(@TempDir tempDir: Path) {
        this.tempDir = tempDir
        tempDir.sub("A/a/1.txt").init()
        tempDir.sub("B/b/2.java").init()
        tempDir.sub("C/c/3.kt").init()
        tempDir.sub("C/d/3.txt").init()
        tempDir.sub("C/d/4.txt").init()
    }

    private fun File.init() {
        FileUtils.forceMkdirParent(this)
        FileUtils.write(this, "hello world")
    }

    private fun Path.sub(path: String): File {
        return File("$this/$path".r())
    }

    /**
     * redirect to real path
     */
    private fun String.r(): String {
        return this.replace("/", separator)
    }

    @Test
    fun testSizeOf() {
        assertEquals(11L, FileSizeUtils.sizeOf(tempDir!!.sub("A/a/1.txt")))
        assertEquals(33L, FileSizeUtils.sizeOf(tempDir!!.sub("C")))
        assertThrows<IllegalArgumentException> {
            FileSizeUtils.sizeOf(tempDir!!.sub("A/a999"))
        }
    }

    @Test
    fun testSizeOfAsBigInteger() {
        assertEquals(BigInteger.valueOf(11), FileSizeUtils.sizeOfAsBigInteger(tempDir!!.sub("A/a/1.txt")))
        assertEquals(BigInteger.valueOf(33), FileSizeUtils.sizeOfAsBigInteger(tempDir!!.sub("C")))
        assertThrows<IllegalArgumentException> {
            FileSizeUtils.sizeOfAsBigInteger(tempDir!!.sub("A/a999"))
        }
    }

    @Test
    fun testSizeOfDirectory() {
        assertThrows<IllegalArgumentException> {
            FileSizeUtils.sizeOfDirectory(tempDir!!.sub("A/a/1.txt"))
        }
        assertEquals(33L, FileSizeUtils.sizeOfDirectory(tempDir!!.sub("C")))
        assertThrows<IllegalArgumentException> {
            FileSizeUtils.sizeOfDirectory(tempDir!!.sub("A/a999"))
        }
    }

    @Test
    fun testSizeOfDirectoryAsBigInteger() {
        assertThrows<IllegalArgumentException> {
            FileSizeUtils.sizeOfDirectoryAsBigInteger(tempDir!!.sub("A/a/1.txt"))
        }
        assertEquals(BigInteger.valueOf(33), FileSizeUtils.sizeOfDirectoryAsBigInteger(tempDir!!.sub("C")))
        assertThrows<IllegalArgumentException> {
            FileSizeUtils.sizeOfDirectoryAsBigInteger(tempDir!!.sub("A/a999"))
        }
    }

}