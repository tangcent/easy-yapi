package com.itangcent.easyapi.exporter.model

import org.junit.Assert.*
import org.junit.Test

class ExportFormatTest {

    @Test
    fun testMarkdownDisplayName() {
        assertEquals("Markdown", ExportFormat.MARKDOWN.displayName)
    }

    @Test
    fun testPostmanDisplayName() {
        assertEquals("Postman", ExportFormat.POSTMAN.displayName)
    }

    @Test
    fun testCurlDisplayName() {
        assertEquals("cURL", ExportFormat.CURL.displayName)
    }

    @Test
    fun testHttpClientDisplayName() {
        assertEquals("HTTP Client", ExportFormat.HTTP_CLIENT.displayName)
    }

    @Test
    fun testAllValues() {
        val values = ExportFormat.values()
        assertEquals(4, values.size)
        assertTrue(values.contains(ExportFormat.MARKDOWN))
        assertTrue(values.contains(ExportFormat.POSTMAN))
        assertTrue(values.contains(ExportFormat.CURL))
        assertTrue(values.contains(ExportFormat.HTTP_CLIENT))
    }

    @Test
    fun testValueOf() {
        assertEquals(ExportFormat.MARKDOWN, ExportFormat.valueOf("MARKDOWN"))
        assertEquals(ExportFormat.POSTMAN, ExportFormat.valueOf("POSTMAN"))
        assertEquals(ExportFormat.CURL, ExportFormat.valueOf("CURL"))
        assertEquals(ExportFormat.HTTP_CLIENT, ExportFormat.valueOf("HTTP_CLIENT"))
    }
}
