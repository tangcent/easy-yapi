package com.itangcent.easyapi.exporter.model

import com.itangcent.easyapi.settings.Settings
import org.junit.Assert.*
import org.junit.Test

class ExportContextTest {

    @Test
    fun testHasSelection() {
        val endpoint1 = ApiEndpoint(
            name = "getUser",
            metadata = HttpMetadata(path = "/api/users/{id}", method = HttpMethod.GET)
        )
        val endpoint2 = ApiEndpoint(
            name = "createUser",
            metadata = HttpMetadata(path = "/api/users", method = HttpMethod.POST)
        )

        val contextWithoutSelection = ExportContext(
            project = mockProject(),
            endpoints = listOf(endpoint1, endpoint2)
        )
        assertFalse(contextWithoutSelection.hasSelection)

        val contextWithSelection = ExportContext(
            project = mockProject(),
            endpoints = listOf(endpoint1, endpoint2),
            selectedEndpoints = listOf(endpoint1)
        )
        assertTrue(contextWithSelection.hasSelection)
    }

    @Test
    fun testEndpointsToExport() {
        val endpoint1 = ApiEndpoint(
            name = "getUser",
            metadata = HttpMetadata(path = "/api/users/{id}", method = HttpMethod.GET)
        )
        val endpoint2 = ApiEndpoint(
            name = "createUser",
            metadata = HttpMetadata(path = "/api/users", method = HttpMethod.POST)
        )

        val contextWithoutSelection = ExportContext(
            project = mockProject(),
            endpoints = listOf(endpoint1, endpoint2)
        )
        assertEquals(2, contextWithoutSelection.endpointsToExport.size)

        val contextWithSelection = ExportContext(
            project = mockProject(),
            endpoints = listOf(endpoint1, endpoint2),
            selectedEndpoints = listOf(endpoint1)
        )
        assertEquals(1, contextWithSelection.endpointsToExport.size)
        assertEquals("getUser", contextWithSelection.endpointsToExport[0].name)
    }

    @Test
    fun testWithSelectedEndpoints() {
        val endpoint1 = ApiEndpoint(
            name = "getUser",
            metadata = HttpMetadata(path = "/api/users/{id}", method = HttpMethod.GET)
        )
        val endpoint2 = ApiEndpoint(
            name = "createUser",
            metadata = HttpMetadata(path = "/api/users", method = HttpMethod.POST)
        )

        val original = ExportContext(
            project = mockProject(),
            endpoints = listOf(endpoint1, endpoint2)
        )
        
        val modified = original.withSelectedEndpoints(listOf(endpoint2))
        
        assertFalse(original.hasSelection)
        assertTrue(modified.hasSelection)
        assertEquals(1, modified.selectedEndpoints.size)
    }

    @Test
    fun testWithExportFormat() {
        val context = ExportContext(
            project = mockProject(),
            endpoints = emptyList()
        )
        
        assertEquals(ExportFormat.MARKDOWN, context.exportFormat)
        
        val modified = context.withExportFormat(ExportFormat.POSTMAN)
        assertEquals(ExportFormat.POSTMAN, modified.exportFormat)
    }

    @Test
    fun testWithOutputConfig() {
        val context = ExportContext(
            project = mockProject(),
            endpoints = emptyList()
        )
        
        val newConfig = OutputConfig(
            outputDir = "/tmp/export",
            fileName = "api.md"
        )
        
        val modified = context.withOutputConfig(newConfig)
        assertEquals("/tmp/export", modified.outputConfig.outputDir)
        assertEquals("api.md", modified.outputConfig.fileName)
    }

    @Test
    fun testOutputConfigDefault() {
        val defaultConfig = OutputConfig.DEFAULT
        assertNull(defaultConfig.outputDir)
        assertNull(defaultConfig.fileName)
    }

    private fun mockProject(): com.intellij.openapi.project.Project {
        return org.mockito.Mockito.mock(com.intellij.openapi.project.Project::class.java)
    }
}
