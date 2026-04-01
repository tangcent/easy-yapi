package com.itangcent.easyapi.exporter.httpclient

import org.junit.Assert.*
import org.junit.Test

class HttpClientExportMetadataTest {

    @Test
    fun testHttpClientExportMetadataCreation() {
        val content = "GET http://example.com"
        val metadata = HttpClientExportMetadata(content)
        assertEquals(content, metadata.content)
    }

    @Test
    fun testFormatDisplayReturnsNull() {
        val metadata = HttpClientExportMetadata("HTTP request")
        assertNull(metadata.formatDisplay())
    }

    @Test
    fun testHttpClientExportMetadataWithEmptyContent() {
        val metadata = HttpClientExportMetadata("")
        assertEquals("", metadata.content)
    }

    @Test
    fun testHttpClientExportMetadataEquality() {
        val metadata1 = HttpClientExportMetadata("GET http://example.com")
        val metadata2 = HttpClientExportMetadata("GET http://example.com")
        assertEquals(metadata1, metadata2)
    }

    @Test
    fun testHttpClientExportMetadataCopy() {
        val original = HttpClientExportMetadata("original content")
        val copy = original.copy(content = "new content")
        assertEquals("new content", copy.content)
        assertEquals("original content", original.content)
    }
}
