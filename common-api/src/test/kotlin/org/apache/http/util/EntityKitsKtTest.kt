package org.apache.http.util

import org.apache.http.HttpEntity
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class EntityKitsKtTest {

    @Test
    fun `test HttpEntity toByteArray`() {
        val content = "Hello, world!"
        val entity = StringEntity(content, ContentType.TEXT_PLAIN)
        val bytes = entity.toByteArray()
        assertContentEquals(content.toByteArray(), bytes)
    }

    @Test
    fun `test HttpEntity consume`() {
        object : HttpEntity {
            override fun getContent() = null
            override fun getContentLength() = 0L
            override fun getContentType() = null
            override fun isChunked() = false
            override fun isRepeatable() = false
            override fun isStreaming() = false
            override fun writeTo(output: java.io.OutputStream?) {}
            override fun getContentEncoding() = null
            override fun consumeContent() {}
        }.consume()
        object : HttpEntity {
            override fun getContent() = "content".byteInputStream(Charsets.UTF_8)
            override fun getContentLength() = 0L
            override fun getContentType() = null
            override fun isChunked() = false
            override fun isRepeatable() = false
            override fun isStreaming() = true
            override fun writeTo(output: java.io.OutputStream?) {}
            override fun getContentEncoding() = null
            override fun consumeContent() {}
        }.consume()
    }

    @Test
    fun `test HttpEntity getContentCharSet`() {
        val contentType = ContentType.create("text/plain", "UTF-8")
        val entity = StringEntity("Hello, world!", contentType)
        val charset = entity.getContentCharSet()
        assertEquals("UTF-8", charset)
    }

    @Test
    fun `test HttpEntity getContentMimeType`() {
        val contentType = ContentType.create("text/plain", "UTF-8")
        val entity = StringEntity("Hello, world!", contentType)
        val mimeType = entity.getContentMimeType()
        assertEquals("text/plain", mimeType)
    }

    @Test
    fun `test HttpEntity readString`() {
        val content = "Hello, world!"
        val entity = StringEntity(content, ContentType.TEXT_PLAIN)
        assertEquals(content, entity.readString())

        val wildcardEntity = StringEntity(content, ContentType.WILDCARD)
        assertEquals(content, wildcardEntity.readString())
        assertEquals(content, wildcardEntity.readString(Charsets.ISO_8859_1))
    }

    @Test
    fun `test HttpEntity readString with default charset`() {
        val content = "Hello, world!"
        val entity = StringEntity(content, ContentType.TEXT_PLAIN.withCharset("ISO-8859-1"))
        assertEquals(content, entity.readString())
    }

    @Test
    fun `test HttpEntity readString with specified charset`() {
        val content = "Hello, world!"
        val entity = StringEntity(content, ContentType.TEXT_PLAIN.withCharset("ISO-8859-1"))
        val result = entity.readString("ISO-8859-1")
        assertEquals(content, result)
    }
}