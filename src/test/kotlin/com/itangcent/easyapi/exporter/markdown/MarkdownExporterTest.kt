package com.itangcent.easyapi.exporter.markdown

import com.itangcent.easyapi.exporter.model.ExportFormat
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*

class MarkdownExporterTest : EasyApiLightCodeInsightFixtureTestCase() {
    
    private lateinit var exporter: MarkdownExporter
    
    override fun setUp() {
        super.setUp()
        exporter = MarkdownExporter(project)
    }
    
    @org.junit.Test
    fun `test formatIsMarkdown`() {
        assertEquals(ExportFormat.MARKDOWN, exporter.format)
    }
    
    @org.junit.Test
    fun `test emptyEndpoints`() {
        val endpoints = emptyList<com.itangcent.easyapi.exporter.model.ApiEndpoint>()
        val context = createTestContext(endpoints)
        val result = runBlocking { exporter.export(context) }
        
        assertTrue("Expected Success but got $result", result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertEquals(0, success.count)
        assertNotNull(success.target)
    }
    
    @org.junit.Test
    fun `test singleEndpoint`() {
        val endpoint = createTestEndpoint(
            name = "Get User",
            path = "/api/users/{id}",
            method = HttpMethod.GET,
            description = "Retrieve user by ID"
        )
        
        val context = createTestContext(listOf(endpoint))
        val result = runBlocking { exporter.export(context) }
        
        assertTrue("Expected Success but got $result", result is ExportResult.Success)
        val success = result as ExportResult.Success
        
        assertEquals(1, success.count)
        assertNotNull(success.metadata)
        assertTrue(success.metadata is MarkdownExportMetadata)
        
        val metadata = success.metadata as MarkdownExportMetadata
        assertNotNull(metadata.content)
        assertTrue(metadata.content.isNotEmpty())
    }
    
    @org.junit.Test
    fun `test multipleEndpoints`() {
        val endpoints = listOf(
            createTestEndpoint("Get Users", "/api/users", HttpMethod.GET),
            createTestEndpoint("Create User", "/api/users", HttpMethod.POST),
            createTestEndpoint("Update User", "/api/users/{id}", HttpMethod.PUT),
            createTestEndpoint("Delete User", "/api/users/{id}", HttpMethod.DELETE)
        )
        
        val context = createTestContext(endpoints)
        val result = runBlocking { exporter.export(context) }
        
        assertTrue("Expected Success but got $result", result is ExportResult.Success)
        val success = result as ExportResult.Success
        
        assertEquals(4, success.count)
        
        val metadata = success.metadata as MarkdownExportMetadata
        assertTrue(metadata.content.contains("Get Users"))
        assertTrue(metadata.content.contains("Create User"))
        assertTrue(metadata.content.contains("Update User"))
        assertTrue(metadata.content.contains("Delete User"))
    }
    
    @org.junit.Test
    fun `test validMarkdownFormat`() {
        val endpoint = createTestEndpoint(
            name = "Test API",
            path = "/test",
            method = HttpMethod.GET,
            description = "Test description"
        )
        
        val context = createTestContext(listOf(endpoint))
        val result = runBlocking { exporter.export(context) }
        
        assertTrue("Expected Success but got $result", result is ExportResult.Success)
        val success = result as ExportResult.Success
        
        val metadata = success.metadata as MarkdownExportMetadata
        val content = metadata.content
        
        assertTrue("Should contain API name", content.contains("Test API"))
        assertTrue("Should contain path", content.contains("/test"))
        assertTrue("Should contain method", content.contains("GET"))
        assertTrue("Should contain description", content.contains("Test description"))
    }
    
    @org.junit.Test
    fun `test httpMethods`() {
        val endpoints = listOf(
            createTestEndpoint("GET Test", "/test", HttpMethod.GET),
            createTestEndpoint("POST Test", "/test", HttpMethod.POST),
            createTestEndpoint("PUT Test", "/test", HttpMethod.PUT),
            createTestEndpoint("DELETE Test", "/test", HttpMethod.DELETE),
            createTestEndpoint("PATCH Test", "/test", HttpMethod.PATCH)
        )
        
        val context = createTestContext(endpoints)
        val result = runBlocking { exporter.export(context) }
        
        assertTrue("Expected Success but got $result", result is ExportResult.Success)
        val success = result as ExportResult.Success
        
        assertEquals(5, success.count)
        
        val metadata = success.metadata as MarkdownExportMetadata
        val content = metadata.content
        
        assertTrue(content.contains("GET"))
        assertTrue(content.contains("POST"))
        assertTrue(content.contains("PUT"))
        assertTrue(content.contains("DELETE"))
        assertTrue(content.contains("PATCH"))
    }
    
    @org.junit.Test
    fun `test groupsEndpoints`() {
        val endpoints = listOf(
            createTestEndpoint("Get User", "/api/users", HttpMethod.GET),
            createTestEndpoint("Create User", "/api/users", HttpMethod.POST),
            createTestEndpoint("Get Order", "/api/orders", HttpMethod.GET),
            createTestEndpoint("Create Order", "/api/orders", HttpMethod.POST)
        )
        
        val context = createTestContext(endpoints)
        val result = runBlocking { exporter.export(context) }
        
        assertTrue("Expected Success but got $result", result is ExportResult.Success)
        val success = result as ExportResult.Success
        
        val metadata = success.metadata as MarkdownExportMetadata
        val content = metadata.content
        
        assertNotNull(content)
        assertTrue(content.isNotEmpty())
    }
    
    @org.junit.Test
    fun `test contentNotEmpty`() {
        val endpoint = createTestEndpoint()
        val context = createTestContext(listOf(endpoint))
        val result = runBlocking { exporter.export(context) }
        
        assertTrue("Expected Success but got $result", result is ExportResult.Success)
        val success = result as ExportResult.Success
        
        val metadata = success.metadata as MarkdownExportMetadata
        assertTrue("Content should not be empty", metadata.content.isNotBlank())
    }
    
    @org.junit.Test
    fun `test noDescription`() {
        val endpoint = createTestEndpoint(
            name = "No Description API",
            path = "/no-desc",
            method = HttpMethod.GET,
            description = null
        )
        
        val context = createTestContext(listOf(endpoint))
        val result = runBlocking { exporter.export(context) }
        
        assertTrue("Expected Success but got $result", result is ExportResult.Success)
        val success = result as ExportResult.Success
        
        assertEquals(1, success.count)
    }
    
    private fun createTestEndpoint(
        name: String = "Test API",
        path: String = "/api/test",
        method: HttpMethod = HttpMethod.GET,
        description: String? = null
    ): com.itangcent.easyapi.exporter.model.ApiEndpoint {
        return com.itangcent.easyapi.exporter.model.ApiEndpoint(
            name = name,
            description = description,
            metadata = com.itangcent.easyapi.exporter.model.HttpMetadata(
                path = path,
                method = method
            )
        )
    }
    
    private fun createTestContext(
        endpoints: List<com.itangcent.easyapi.exporter.model.ApiEndpoint>
    ): com.itangcent.easyapi.exporter.model.ExportContext {
        return com.itangcent.easyapi.exporter.model.ExportContext(
            project = project,
            endpoints = endpoints,
            exportFormat = ExportFormat.MARKDOWN,
            settings = com.itangcent.easyapi.settings.Settings(),
            outputConfig = com.itangcent.easyapi.exporter.model.OutputConfig(),
            actionContext = actionContext
        )
    }
}
