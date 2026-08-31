package com.itangcent.easyapi.core.config

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.file.Files

/**
 * Tests for [RuleFileTextIo] — the UTF-8-with-BOM convention for rule files
 * (issue #755).
 *
 * Plain unit test: the helper is pure `java.nio` with no IDE dependency.
 * The BOM string is built from its three bytes so the test source itself
 * contains only visible ASCII.
 */
class RuleFileTextIoTest {

    private val bom = String(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), Charsets.UTF_8)
    private val bomBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    @Test
    fun testWriteUtf8WithBomPrependsBomBytes() {
        val path = Files.createTempFile("easyapi-bom-", ".properties")
        try {
            RuleFileTextIo.writeUtf8WithBom(path, "api.name=测试")
            val bytes = Files.readAllBytes(path)
            assertArrayEquals(
                "file should start with the UTF-8 BOM bytes",
                bomBytes,
                bytes.copyOfRange(0, 3)
            )
            assertEquals(
                "content after the BOM should be the UTF-8 payload",
                "api.name=测试",
                String(bytes.copyOfRange(3, bytes.size), Charsets.UTF_8)
            )
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun testWriteUtf8WithBomWritesEmptyContentAsBomOnly() {
        val path = Files.createTempFile("easyapi-bom-empty-", ".properties")
        try {
            RuleFileTextIo.writeUtf8WithBom(path, "")
            assertArrayEquals(
                "an empty rule file should still carry the BOM so IDEA detects UTF-8",
                bomBytes,
                Files.readAllBytes(path)
            )
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun testWriteUtf8WithBomDoesNotDuplicateBom() {
        val path = Files.createTempFile("easyapi-bom-dup-", ".properties")
        try {
            RuleFileTextIo.writeUtf8WithBom(path, bom + "api.name=x")
            val bytes = Files.readAllBytes(path)
            assertArrayEquals(
                "an existing BOM in the content must be replaced, not duplicated",
                bomBytes,
                bytes.copyOfRange(0, 3)
            )
            assertFalse(
                "no second BOM may appear after the first",
                bytes.copyOfRange(3, bytes.size).take(3).toByteArray().contentEquals(bomBytes)
            )
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun testReadUtf8StrippingBomRemovesBom() {
        val path = Files.createTempFile("easyapi-bom-read-", ".properties")
        try {
            Files.write(path, (bom + "api.name=值").toByteArray(Charsets.UTF_8))
            assertEquals("api.name=值", RuleFileTextIo.readUtf8StrippingBom(path))
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun testReadUtf8StrippingBomKeepsBomlessContent() {
        val path = Files.createTempFile("easyapi-nobom-", ".properties")
        try {
            Files.write(path, "api.name=plain".toByteArray(Charsets.UTF_8))
            assertEquals("api.name=plain", RuleFileTextIo.readUtf8StrippingBom(path))
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun testStripBom() {
        assertEquals("api.name=x", RuleFileTextIo.stripBom(bom + "api.name=x"))
        assertEquals("api.name=x", RuleFileTextIo.stripBom("api.name=x"))
        // Only a leading BOM is stripped; an embedded one is preserved.
        assertEquals("a$bom", RuleFileTextIo.stripBom("a$bom"))
    }
}
