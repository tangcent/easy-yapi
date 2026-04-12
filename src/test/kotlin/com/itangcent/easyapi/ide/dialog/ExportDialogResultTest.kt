package com.itangcent.easyapi.ide.dialog

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ExportFormat
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.OutputConfig
import org.junit.Assert.*
import org.junit.Test

class ExportDialogResultTest {

    @Test
    fun testProperties() {
        val config = OutputConfig()
        val result = ExportDialogResult(ExportFormat.MARKDOWN, config)
        assertEquals(ExportFormat.MARKDOWN, result.format)
        assertSame(config, result.outputConfig)
        assertTrue(result.selectedEndpoints.isEmpty())
    }

    @Test
    fun testEquality() {
        val config = OutputConfig()
        val r1 = ExportDialogResult(ExportFormat.MARKDOWN, config)
        val r2 = ExportDialogResult(ExportFormat.MARKDOWN, config)
        assertEquals(r1, r2)
    }

    @Test
    fun testInequality_differentFormat() {
        val config = OutputConfig()
        val r1 = ExportDialogResult(ExportFormat.MARKDOWN, config)
        val r2 = ExportDialogResult(ExportFormat.CURL, config)
        assertNotEquals(r1, r2)
    }

    @Test
    fun testCopy() {
        val config = OutputConfig()
        val original = ExportDialogResult(ExportFormat.MARKDOWN, config)
        val copy = original.copy(format = ExportFormat.CURL)
        assertEquals(ExportFormat.CURL, copy.format)
        assertSame(config, copy.outputConfig)
    }

    @Test
    fun testWithSelectedEndpoints() {
        val config = OutputConfig()
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = HttpMetadata(path = "/api/users", method = HttpMethod.GET)
        )
        val selection = EndpointSelection(endpoint)
        val result = ExportDialogResult(ExportFormat.MARKDOWN, config, listOf(selection))

        assertEquals(1, result.selectedEndpoints.size)
        assertSame(endpoint, result.selectedEndpoints[0].endpoint)
    }

    @Test
    fun testEndpointSelectionProperties() {
        val endpoint = ApiEndpoint(
            name = "Create User",
            metadata = HttpMetadata(path = "/api/users", method = HttpMethod.POST)
        )
        val selection = EndpointSelection(endpoint)
        assertSame(endpoint, selection.endpoint)
    }

    @Test
    fun testEndpointSelectionEquality() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = HttpMetadata(path = "/api/users", method = HttpMethod.GET)
        )
        val s1 = EndpointSelection(endpoint)
        val s2 = EndpointSelection(endpoint)
        assertEquals(s1, s2)
    }
}
