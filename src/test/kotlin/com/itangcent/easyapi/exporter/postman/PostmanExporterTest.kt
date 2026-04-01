package com.itangcent.easyapi.exporter.postman

import com.itangcent.easyapi.exporter.model.ExportFormat
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.OutputConfig
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*

class PostmanExporterTest : EasyApiLightCodeInsightFixtureTestCase() {
    
    private lateinit var exporter: PostmanExporter
    
    override fun setUp() {
        super.setUp()
        exporter = PostmanExporter(project)
    }
    
    @org.junit.Test
    fun `test format is POSTMAN`() {
        assertEquals(ExportFormat.POSTMAN, exporter.format)
    }
    
    @org.junit.Test
    fun `test export with empty endpoints returns success with zero count`() {
        val endpoints = emptyList<com.itangcent.easyapi.exporter.model.ApiEndpoint>()
        val context = createTestContext(endpoints)
        val result = runBlocking { exporter.export(context) }
        
        assertTrue("Expected Success but got $result", result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertEquals(0, success.count)
        assertEquals("Postman Collection", success.target)
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
        assertTrue(success.metadata is PostmanExportMetadata)
        
        val metadata = success.metadata as PostmanExportMetadata
        assertNotNull(metadata.collectionData)
        assertTrue(metadata.collectionData is com.itangcent.easyapi.exporter.postman.model.PostmanCollection)
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
        
        val metadata = success.metadata as PostmanExportMetadata
        val collection = metadata.collectionData!!
        val totalCount = countApiItems(collection)
        assertEquals(4, totalCount)
    }
    
    private fun countApiItems(collection: com.itangcent.easyapi.exporter.postman.model.PostmanCollection): Int {
        fun count(items: List<com.itangcent.easyapi.exporter.postman.model.PostmanItem>): Int = items.sumOf { item ->
            if (item.request != null) 1 else count(item.item)
        }
        return count(collection.item)
    }
    
    @org.junit.Test
    fun `test export creates valid postman collection structure`() {
        val endpoint = createTestEndpoint(
            name = "Test API",
            path = "/test",
            method = HttpMethod.GET
        )
        
        val context = createTestContext(listOf(endpoint))
        val result = runBlocking { exporter.export(context) }
        
        assertTrue("Expected Success but got $result", result is ExportResult.Success)
        val success = result as ExportResult.Success
        
        val metadata = success.metadata as PostmanExportMetadata
        val collection = metadata.collectionData!!
        
        assertNotNull(collection.info)
        assertNotNull(collection.info.name)
        assertNotNull(collection.info.schema)
        assertTrue(collection.item.isNotEmpty())
    }
    
    @org.junit.Test
    fun `test export groups endpoints by path`() {
        val endpoints = listOf(
            createTestEndpoint("Get User", "/api/users", HttpMethod.GET),
            createTestEndpoint("Create User", "/api/users", HttpMethod.POST),
            createTestEndpoint("Get Order", "/api/orders", HttpMethod.GET)
        )

        val context = createTestContext(endpoints)
        val result = runBlocking { exporter.export(context) }

        assertTrue("Expected Success but got $result", result is ExportResult.Success)
        val success = result as ExportResult.Success

        val metadata = success.metadata as PostmanExportMetadata
        val collection = metadata.collectionData!!

        assertTrue("Expected at least 1 folder item", collection.item.isNotEmpty())
        val totalCount = countApiItems(collection)
        assertEquals("Expected 3 endpoints total", 3, totalCount)
    }
    
    @org.junit.Test
    fun `test export with different http methods`() {
        val endpoints = HttpMethod.values().map { method ->
            createTestEndpoint(
                name = "${method.name} Test",
                path = "/test",
                method = method
            )
        }
        
        val context = createTestContext(endpoints)
        val result = runBlocking { exporter.export(context) }
        
        assertTrue("Expected Success but got $result", result is ExportResult.Success)
        val success = result as ExportResult.Success
        
        assertEquals(endpoints.size, success.count)
    }
    
    @org.junit.Test
    fun `test export context contains correct project`() {
        val endpoint = createTestEndpoint()
        val context = createTestContext(listOf(endpoint))
        
        assertEquals(project, context.project)
        assertEquals(ExportFormat.POSTMAN, context.exportFormat)
    }
    
    @org.junit.Test
    fun `test export with output config`() {
        val endpoint = createTestEndpoint()
        val outputConfig = OutputConfig(
            fileName = "test_collection",
            postmanOptions = null
        )
        val context = createTestContext(listOf(endpoint), outputConfig)
        
        val result = runBlocking { exporter.export(context) }
        
        assertTrue("Expected Success but got $result", result is ExportResult.Success)
    }
    
    private fun createTestEndpoint(
        name: String = "Test API",
        path: String = "/api/test",
        method: HttpMethod = HttpMethod.GET,
        description: String? = null
    ): com.itangcent.easyapi.exporter.model.ApiEndpoint {
        return com.itangcent.easyapi.exporter.model.ApiEndpoint(
            name = name,
            path = path,
            method = method,
            description = description
        )
    }
    
    private fun createTestContext(
        endpoints: List<com.itangcent.easyapi.exporter.model.ApiEndpoint>,
        outputConfig: OutputConfig = OutputConfig()
    ): com.itangcent.easyapi.exporter.model.ExportContext {
        return com.itangcent.easyapi.exporter.model.ExportContext(
            project = project,
            endpoints = endpoints,
            exportFormat = ExportFormat.POSTMAN,
            settings = com.itangcent.easyapi.settings.Settings(),
            outputConfig = outputConfig,
            actionContext = actionContext
        )
    }
}
