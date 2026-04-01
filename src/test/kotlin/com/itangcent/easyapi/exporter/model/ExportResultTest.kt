package com.itangcent.easyapi.exporter.model

import org.junit.Assert.*
import org.junit.Test

class ExportResultTest {

    @Test
    fun testSuccessResult() {
        val metadata = object : ExportMetadata {
            override fun formatDisplay(): String? = "test"
        }
        val result = ExportResult.Success(
            count = 10,
            target = "/path/to/file.json",
            metadata = metadata
        )
        assertEquals(10, result.count)
        assertEquals("/path/to/file.json", result.target)
        assertNotNull(result.metadata)
    }

    @Test
    fun testSuccessResultWithoutMetadata() {
        val result = ExportResult.Success(
            count = 5,
            target = "output.md"
        )
        assertEquals(5, result.count)
        assertEquals("output.md", result.target)
        assertNull(result.metadata)
    }

    @Test
    fun testErrorResult() {
        val result = ExportResult.Error("Export failed: file not found")
        assertEquals("Export failed: file not found", result.message)
    }

    @Test
    fun testCancelledResult() {
        val result = ExportResult.Cancelled
        assertSame(ExportResult.Cancelled, result)
    }

    @Test
    fun testSuccessEquality() {
        val result1 = ExportResult.Success(10, "/path/file.json")
        val result2 = ExportResult.Success(10, "/path/file.json")
        assertEquals(result1, result2)
    }

    @Test
    fun testErrorEquality() {
        val result1 = ExportResult.Error("Error message")
        val result2 = ExportResult.Error("Error message")
        assertEquals(result1, result2)
    }

    @Test
    fun testSuccessCopy() {
        val original = ExportResult.Success(10, "/path/file.json")
        val copy = original.copy(count = 20)
        assertEquals(20, copy.count)
        assertEquals(10, original.count)
    }

    @Test
    fun testWhenExpression() {
        val results = listOf(
            ExportResult.Success(10, "file1.json"),
            ExportResult.Error("Error occurred"),
            ExportResult.Cancelled
        )

        val messages = results.map { result ->
            when (result) {
                is ExportResult.Success -> "Exported ${result.count} endpoints to ${result.target}"
                is ExportResult.Error -> "Error: ${result.message}"
                ExportResult.Cancelled -> "Export was cancelled"
            }
        }

        assertEquals(3, messages.size)
        assertEquals("Exported 10 endpoints to file1.json", messages[0])
        assertEquals("Error: Error occurred", messages[1])
        assertEquals("Export was cancelled", messages[2])
    }
}
