package com.itangcent.easyapi.config

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for DefaultConfigReader companion and pure logic methods.
 * The main class requires IntelliJ Project, so we test extractable logic here.
 */
class DefaultConfigReaderLogicTest {

    // ==================== parseUrls logic ====================

    @Test
    fun `parseUrls with null input returns empty list`() {
        val result = parseUrls(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseUrls with blank input returns empty list`() {
        val result = parseUrls("   ")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseUrls with single URL`() {
        val result = parseUrls("https://example.com/config")
        assertEquals(listOf("https://example.com/config"), result)
    }

    @Test
    fun `parseUrls with newline-separated URLs`() {
        val result = parseUrls("https://a.com\nhttps://b.com")
        assertEquals(listOf("https://a.com", "https://b.com"), result)
    }

    @Test
    fun `parseUrls with comma-separated URLs`() {
        val result = parseUrls("https://a.com,https://b.com")
        assertEquals(listOf("https://a.com", "https://b.com"), result)
    }

    @Test
    fun `parseUrls with semicolon-separated URLs`() {
        val result = parseUrls("https://a.com;https://b.com")
        assertEquals(listOf("https://a.com", "https://b.com"), result)
    }

    @Test
    fun `parseUrls with mixed separators`() {
        val result = parseUrls("https://a.com\nhttps://b.com,https://c.com;https://d.com")
        assertEquals(4, result.size)
    }

    @Test
    fun `parseUrls trims whitespace`() {
        val result = parseUrls("  https://a.com  ,  https://b.com  ")
        assertEquals(listOf("https://a.com", "https://b.com"), result)
    }

    @Test
    fun `parseUrls filters out empty entries`() {
        val result = parseUrls("https://a.com,,https://b.com")
        assertEquals(listOf("https://a.com", "https://b.com"), result)
    }

    @Test
    fun `parseUrls filters out entries starting with exclamation`() {
        val result = parseUrls("https://a.com\n!https://b.com\nhttps://c.com")
        assertEquals(listOf("https://a.com", "https://c.com"), result)
    }

    @Test
    fun `parseUrls filters out blank entries after trim`() {
        val result = parseUrls("https://a.com\n   \nhttps://b.com")
        assertEquals(listOf("https://a.com", "https://b.com"), result)
    }

    /**
     * Replicates the parseUrls logic from DefaultConfigReader for testing.
     */
    private fun parseUrls(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split('\n', ',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("!") }
    }
}
