package com.itangcent.easyapi.core.http

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files

class ResponseBodyClassifierTest {

    @Test
    fun `text content types are classified as text`() {
        assertTrue(ResponseBodyClassifier.isTextContentType("text/plain"))
        assertTrue(ResponseBodyClassifier.isTextContentType("text/html"))
        assertTrue(ResponseBodyClassifier.isTextContentType("application/json"))
        assertTrue(ResponseBodyClassifier.isTextContentType("application/xml"))
        assertTrue(ResponseBodyClassifier.isTextContentType("application/javascript"))
        assertTrue(ResponseBodyClassifier.isTextContentType("application/graphql"))
        assertTrue(ResponseBodyClassifier.isTextContentType("application/vnd.api+json"))
        assertTrue(ResponseBodyClassifier.isTextContentType("application/atom+xml"))
    }

    @Test
    fun `binary content types are classified as binary`() {
        assertFalse(ResponseBodyClassifier.isTextContentType("application/octet-stream"))
        assertFalse(ResponseBodyClassifier.isTextContentType("image/png"))
        assertFalse(ResponseBodyClassifier.isTextContentType("application/pdf"))
        assertFalse(ResponseBodyClassifier.isTextContentType("application/zip"))
        assertFalse(ResponseBodyClassifier.isTextContentType("video/mp4"))
    }

    @Test
    fun `null and blank default to binary`() {
        assertFalse(ResponseBodyClassifier.isTextContentType(null))
        assertFalse(ResponseBodyClassifier.isTextContentType(""))
        assertFalse(ResponseBodyClassifier.isTextContentType("   "))
    }

    @Test
    fun `charset parameter is ignored`() {
        assertTrue(ResponseBodyClassifier.isTextContentType("application/json; charset=utf-8"))
        assertTrue(ResponseBodyClassifier.isTextContentType("text/plain;charset=UTF-8"))
    }

    @Test
    fun `case is ignored`() {
        assertTrue(ResponseBodyClassifier.isTextContentType("Application/JSON"))
        assertTrue(ResponseBodyClassifier.isTextContentType("TEXT/HTML"))
    }

    @Test
    fun `extractFilename parses plain filename`() {
        assertEquals("report.pdf", ResponseBodyClassifier.extractFilename("attachment; filename=\"report.pdf\""))
        assertEquals("data.csv", ResponseBodyClassifier.extractFilename("attachment; filename=data.csv"))
    }

    @Test
    fun `extractFilename parses RFC 5987 filename star`() {
        assertEquals("报告.pdf", ResponseBodyClassifier.extractFilename("attachment; filename*=UTF-8''%E6%8A%A5%E5%91%8A.pdf"))
    }

    @Test
    fun `extractFilename returns null when absent`() {
        assertNull(ResponseBodyClassifier.extractFilename(null))
        assertNull(ResponseBodyClassifier.extractFilename(""))
        assertNull(ResponseBodyClassifier.extractFilename("inline"))
    }
}

class ResponseBodyReaderTest {

    @Test
    fun `text content decodes to Text`() {
        val body = ResponseBodyReader.read(
            ByteArrayInputStream("hello".toByteArray()), "text/plain", 5
        )
        assertTrue(body is ResponseBody.Text)
        assertEquals("hello", (body as ResponseBody.Text).value)
    }

    @Test
    fun `small binary reads to Bytes`() {
        val bytes = byteArrayOf(0, 1, 2, 0xFF.toByte(), 0xFE.toByte())
        val body = ResponseBodyReader.read(ByteArrayInputStream(bytes), "application/octet-stream", bytes.size.toLong())
        assertTrue(body is ResponseBody.Bytes)
        assertArrayEquals(bytes, (body as ResponseBody.Bytes).value)
    }

    @Test
    fun `large binary spills to temp file`() {
        val body = ResponseBodyReader.read(
            ByteArrayInputStream(ByteArray(10)), "application/octet-stream", ResponseBody.MAX_IN_MEMORY_BYTES + 1
        )
        assertTrue(body is ResponseBody.File)
        val file = body as ResponseBody.File
        assertTrue(Files.exists(file.path))
        Files.deleteIfExists(file.path)
    }

    @Test
    fun `null input returns null`() {
        assertNull(ResponseBodyReader.read(null, "application/json", 0))
    }

    @Test
    fun `sizeOf reports byte counts`() {
        assertNull(ResponseBodyReader.sizeOf(null))
        assertEquals(5L, ResponseBodyReader.sizeOf(ResponseBody.Text("hello")))
        assertEquals(3L, ResponseBodyReader.sizeOf(ResponseBody.Bytes(byteArrayOf(1, 2, 3))))
    }
}

class ResponseBodyWriterTest {

    @Test
    fun `save writes text as UTF-8`() {
        val target = Files.createTempFile("easyapi-test", null).toFile()
        try {
            assertTrue(ResponseBodyWriter.save(ResponseBody.Text("héllo"), target))
            assertArrayEquals("héllo".toByteArray(Charsets.UTF_8), target.readBytes())
        } finally {
            target.delete()
        }
    }

    @Test
    fun `save writes bytes verbatim`() {
        val bytes = byteArrayOf(0, 1, 2, 0xFF.toByte())
        val target = Files.createTempFile("easyapi-test", null).toFile()
        try {
            assertTrue(ResponseBodyWriter.save(ResponseBody.Bytes(bytes), target))
            assertArrayEquals(bytes, target.readBytes())
        } finally {
            target.delete()
        }
    }

    @Test
    fun `save copies spilled file`() {
        val src = Files.createTempFile("easyapi-src", null)
        Files.write(src, byteArrayOf(9, 8, 7))
        val target = Files.createTempFile("easyapi-test", null).toFile()
        try {
            assertTrue(ResponseBodyWriter.save(ResponseBody.File(src), target))
            assertArrayEquals(byteArrayOf(9, 8, 7), target.readBytes())
        } finally {
            Files.deleteIfExists(src)
            target.delete()
        }
    }

    @Test
    fun `save returns false for null body`() {
        val target = Files.createTempFile("easyapi-test", null).toFile()
        try {
            assertFalse(ResponseBodyWriter.save(null, target))
        } finally {
            target.delete()
        }
    }
}
