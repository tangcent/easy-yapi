package com.itangcent.easyapi.exporter.markdown

import org.junit.Assert.*
import org.junit.Test

class MarkdownExportMetadataTest {

    @Test
    fun testMarkdownExportMetadataCreation() {
        val content = "# API Documentation\n\n## Endpoints"
        val metadata = MarkdownExportMetadata(content)
        assertEquals(content, metadata.content)
    }

    @Test
    fun testFormatDisplayReturnsNull() {
        val metadata = MarkdownExportMetadata("# Test")
        assertNull(metadata.formatDisplay())
    }

    @Test
    fun testMarkdownExportMetadataWithEmptyContent() {
        val metadata = MarkdownExportMetadata("")
        assertEquals("", metadata.content)
    }

    @Test
    fun testMarkdownExportMetadataEquality() {
        val metadata1 = MarkdownExportMetadata("# API")
        val metadata2 = MarkdownExportMetadata("# API")
        assertEquals(metadata1, metadata2)
    }

    @Test
    fun testMarkdownExportMetadataCopy() {
        val original = MarkdownExportMetadata("# Original")
        val copy = original.copy(content = "# New")
        assertEquals("# New", copy.content)
        assertEquals("# Original", original.content)
    }

    @Test
    fun testMarkdownExportMetadataWithComplexContent() {
        val content = """
            # API Documentation
            
            ## Overview
            
            This API provides user management functionality.
            
            ## Endpoints
            
            ### GET /api/users
            
            Returns a list of users.
            
            ### POST /api/users
            
            Creates a new user.
        """.trimIndent()
        val metadata = MarkdownExportMetadata(content)
        assertEquals(content, metadata.content)
    }
}
