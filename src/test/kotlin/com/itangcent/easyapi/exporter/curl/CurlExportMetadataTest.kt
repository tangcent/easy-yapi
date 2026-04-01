package com.itangcent.easyapi.exporter.curl

import org.junit.Assert.*
import org.junit.Test

class CurlExportMetadataTest {

    @Test
    fun testCurlExportMetadataCreation() {
        val content = "curl -X GET http://example.com"
        val metadata = CurlExportMetadata(content)
        assertEquals(content, metadata.content)
    }

    @Test
    fun testFormatDisplayReturnsNull() {
        val metadata = CurlExportMetadata("curl command")
        assertNull(metadata.formatDisplay())
    }

    @Test
    fun testCurlExportMetadataWithEmptyContent() {
        val metadata = CurlExportMetadata("")
        assertEquals("", metadata.content)
    }

    @Test
    fun testCurlExportMetadataEquality() {
        val metadata1 = CurlExportMetadata("curl -X GET http://example.com")
        val metadata2 = CurlExportMetadata("curl -X GET http://example.com")
        assertEquals(metadata1, metadata2)
    }

    @Test
    fun testCurlExportMetadataCopy() {
        val original = CurlExportMetadata("original content")
        val copy = original.copy(content = "new content")
        assertEquals("new content", copy.content)
        assertEquals("original content", original.content)
    }
}
