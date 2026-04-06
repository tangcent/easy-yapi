package com.itangcent.easyapi.exporter.httpclient

import com.itangcent.easyapi.exporter.model.ExportFormat
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*

class HttpClientExporterTest : EasyApiLightCodeInsightFixtureTestCase() {
    
    private lateinit var exporter: HttpClientExporter
    
    override fun setUp() {
        super.setUp()
        exporter = HttpClientExporter(project)
    }
    
    @org.junit.Test
    fun `test format is HTTP_CLIENT`() {
        assertEquals(ExportFormat.HTTP_CLIENT, exporter.format)
    }
    
    @org.junit.Test
    fun `test export with empty endpoints returns success with zero count`() {
        val endpoints = emptyList<com.itangcent.easyapi.exporter.model.ApiEndpoint>()
        val context = createTestContext(endpoints)
        
        val result = runBlocking { exporter.export(context) }
        
        assertTrue("Expected Success but got $result", result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertEquals(0, success.count)
        assertNotNull(success.target)
    }
    
    @org.junit.Test
    fun `test export with single endpoint`() {
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
        assertTrue(success.metadata is HttpClientExportMetadata)
        
        val metadata = success.metadata as HttpClientExportMetadata
        assertNotNull(metadata.content)
        assertTrue(metadata.content.isNotEmpty())
    }
    
    @org.junit.Test
    fun `test export with multiple endpoints`() {
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
        
        val metadata = success.metadata as HttpClientExportMetadata
        assertTrue(metadata.content.contains("###"))
    }
    
    @org.junit.Test
    fun `test export generates valid http client format`() {
        val endpoint = createTestEndpoint(
            name = "Test API",
            path = "/test",
            method = HttpMethod.POST
        )
        
        val context = createTestContext(listOf(endpoint))
        val result = runBlocking { exporter.export(context) }
        
        assertTrue("Expected Success but got $result", result is ExportResult.Success)
        val success = result as ExportResult.Success
        
        val metadata = success.metadata as HttpClientExportMetadata
        val content = metadata.content
        
        assertTrue("Should contain HTTP method", content.contains("POST"))
        assertTrue("Should contain path", content.contains("/test"))
    }
    
    @org.junit.Test
    fun `test export groups requests by name`() {
        val endpoints = listOf(
            createTestEndpoint("Get User", "/api/users", HttpMethod.GET),
            createTestEndpoint("Create User", "/api/users", HttpMethod.POST)
        )
        
        val context = createTestContext(endpoints)
        val result = runBlocking { exporter.export(context) }
        
        assertTrue("Expected Success but got $result", result is ExportResult.Success)
        val success = result as ExportResult.Success
        
        val metadata = success.metadata as HttpClientExportMetadata
        assertTrue("Should contain request separator", metadata.content.contains("###"))
    }
    
    @org.junit.Test
    fun `test export with different http methods`() {
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
        
        val metadata = success.metadata as HttpClientExportMetadata
        val content = metadata.content
        
        assertTrue(content.contains("GET"))
        assertTrue(content.contains("POST"))
        assertTrue(content.contains("PUT"))
        assertTrue(content.contains("DELETE"))
        assertTrue(content.contains("PATCH"))
    }
    
    @org.junit.Test
    fun `test export content is not empty`() {
        val endpoint = createTestEndpoint()
        val context = createTestContext(listOf(endpoint))
        val result = runBlocking { exporter.export(context) }
        
        assertTrue("Expected Success but got $result", result is ExportResult.Success)
        val success = result as ExportResult.Success
        
        val metadata = success.metadata as HttpClientExportMetadata
        assertTrue("Content should not be empty", metadata.content.isNotBlank())
    }
    
    @org.junit.Test
    fun `test export with endpoint having path parameters`() {
        val endpoint = createTestEndpoint(
            name = "Get User by ID",
            path = "/api/users/{userId}/posts/{postId}",
            method = HttpMethod.GET
        )
        
        val context = createTestContext(listOf(endpoint))
        val result = runBlocking { exporter.export(context) }
        
        assertTrue("Expected Success but got $result", result is ExportResult.Success)
        val success = result as ExportResult.Success
        
        assertEquals(1, success.count)
        
        val metadata = success.metadata as HttpClientExportMetadata
        assertTrue(metadata.content.contains("GET"))
    }
    
    private fun createTestEndpoint(
        name: String = "Test API",
        path: String = "/api/test",
        method: HttpMethod = HttpMethod.GET,
        description: String? = null
    ): com.itangcent.easyapi.exporter.model.ApiEndpoint {
        return com.itangcent.easyapi.exporter.model.ApiEndpoint(
            name = name,
            metadata = com.itangcent.easyapi.exporter.model.HttpMetadata(
                path = path,
                method = method
            ),
            description = description
        )
    }
    
    private fun createTestContext(
        endpoints: List<com.itangcent.easyapi.exporter.model.ApiEndpoint>
    ): com.itangcent.easyapi.exporter.model.ExportContext {
        return com.itangcent.easyapi.exporter.model.ExportContext(
            project = project,
            endpoints = endpoints,
            exportFormat = ExportFormat.HTTP_CLIENT,
            settings = com.itangcent.easyapi.settings.Settings(),
            outputConfig = com.itangcent.easyapi.exporter.model.OutputConfig(host = "http://localhost:8080"),
            actionContext = actionContext
        )
    }
}
