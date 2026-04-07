package com.itangcent.easyapi.rule.parser

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ScriptFilesWrapperTest {

    @Test
    fun testSave_createsFile() {
        val tmpDir = System.getProperty("java.io.tmpdir")
        val path = "$tmpDir/easyapi-test-${System.nanoTime()}/test.txt"
        try {
            ScriptFilesWrapper.save("hello world", path)
            val file = File(path)
            assertTrue(file.exists())
            assertEquals("hello world", file.readText())
        } finally {
            File(path).delete()
            File(path).parentFile?.delete()
        }
    }

    @Test
    fun testSave_withCharset() {
        val tmpDir = System.getProperty("java.io.tmpdir")
        val path = "$tmpDir/easyapi-test-${System.nanoTime()}/charset.txt"
        try {
            ScriptFilesWrapper.save("hello", "UTF-8", path)
            val file = File(path)
            assertTrue(file.exists())
            assertEquals("hello", file.readText(Charsets.UTF_8))
        } finally {
            File(path).delete()
            File(path).parentFile?.delete()
        }
    }

    @Test
    fun testSave_withInvalidCharset_fallsBackToUtf8() {
        val tmpDir = System.getProperty("java.io.tmpdir")
        val path = "$tmpDir/easyapi-test-${System.nanoTime()}/fallback.txt"
        try {
            ScriptFilesWrapper.save("hello", "INVALID-CHARSET", path)
            val file = File(path)
            assertTrue(file.exists())
            assertEquals("hello", file.readText(Charsets.UTF_8))
        } finally {
            File(path).delete()
            File(path).parentFile?.delete()
        }
    }

    @Test
    fun testSave_createsParentDirectories() {
        val tmpDir = System.getProperty("java.io.tmpdir")
        val path = "$tmpDir/easyapi-test-${System.nanoTime()}/nested/dir/file.txt"
        try {
            ScriptFilesWrapper.save("content", path)
            assertTrue(File(path).exists())
        } finally {
            File(path).delete()
            File(path).parentFile?.delete()
            File(path).parentFile?.parentFile?.delete()
            File(path).parentFile?.parentFile?.parentFile?.delete()
        }
    }

    @Test
    fun testSave_overwritesExistingFile() {
        val tmpDir = System.getProperty("java.io.tmpdir")
        val path = "$tmpDir/easyapi-test-${System.nanoTime()}/overwrite.txt"
        try {
            ScriptFilesWrapper.save("first", path)
            ScriptFilesWrapper.save("second", path)
            assertEquals("second", File(path).readText())
        } finally {
            File(path).delete()
            File(path).parentFile?.delete()
        }
    }
}
