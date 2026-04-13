package com.itangcent.easyapi.exporter.postman

import com.itangcent.easyapi.exporter.model.*
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

class PostmanExporterTest : EasyApiLightCodeInsightFixtureTestCase() {
    
    private lateinit var exporter: PostmanExporter
    
    override fun setUp() {
        super.setUp()
        exporter = PostmanExporter(project)
    }
    
    @org.junit.Test
    fun `test formatIsPostman`() {
        assertEquals(ExportFormat.POSTMAN, exporter.format)
    }
    
    @org.junit.Test
    fun `test emptyEndpoints`() {
        val endpoints = emptyList<com.itangcent.easyapi.exporter.model.ApiEndpoint>()
        val context = createTestContext(endpoints)
        val result = runBlocking { exporter.export(context) }
        
        assertTrue("Expected Success but got $result", result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertEquals(0, success.count)
    }
    
    @org.junit.Test
    fun `test singleEndpoint`() {
        val endpoint = createTestEndpoint(
            name = "Get User",
            path = "/api/users/{id}",
            method = HttpMethod.GET,
            description = "Retrieve user by ID"
        )
        
        val context = createTestContext(
            endpoints = listOf(endpoint),
            outputConfig = OutputConfig(
                postmanOptions = PostmanExportOptions(selectedCollectionName = "Test API")
            )
        )
        val result = runBlocking { exporter.export(context) }
        
        assertTrue("Expected Success but got $result", result is ExportResult.Success)
        val success = result as ExportResult.Success
        
        assertEquals(1, success.count)
        assertNotNull(success.metadata)
        assertTrue(success.metadata is PostmanExportMetadata)
        
        val metadata = success.metadata as PostmanExportMetadata
        assertNotNull(metadata.collectionData)
        assertEquals("Test API", metadata.collectionData?.info?.name)
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
        
        val metadata = success.metadata as PostmanExportMetadata
        val items = metadata.collectionData?.item ?: emptyList()
        assertTrue(items.any { it.name == "Get Users" })
        assertTrue(items.any { it.name == "Create User" })
        assertTrue(items.any { it.name == "Update User" })
        assertTrue(items.any { it.name == "Delete User" })
    }
    
    @org.junit.Test
    fun `test validPostmanFormat`() {
        val endpoint = createTestEndpoint(
            name = "Test API",
            path = "/test",
            method = HttpMethod.GET,
            description = "Test description"
        )
        
        val context = createTestContext(
            endpoints = listOf(endpoint),
            outputConfig = OutputConfig(
                postmanOptions = PostmanExportOptions(selectedCollectionName = "Test API")
            )
        )
        val result = runBlocking { exporter.export(context) }
        
        assertTrue("Expected Success but got $result", result is ExportResult.Success)
        val success = result as ExportResult.Success
        
        val metadata = success.metadata as PostmanExportMetadata
        val collection = metadata.collectionData
        
        assertNotNull(collection?.info)
        assertEquals("Test API", collection?.info?.name)
        
        val item = collection?.item?.firstOrNull()
        assertNotNull(item)
        assertEquals("Test API", item?.name)
        
        val request = item?.request
        assertNotNull(request)
        assertEquals("GET", request?.method)
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
        
        val metadata = success.metadata as PostmanExportMetadata
        val items = metadata.collectionData?.item ?: emptyList()
        
        assertTrue(items.any { it.request?.method == "GET" })
        assertTrue(items.any { it.request?.method == "POST" })
        assertTrue(items.any { it.request?.method == "PUT" })
        assertTrue(items.any { it.request?.method == "DELETE" })
        assertTrue(items.any { it.request?.method == "PATCH" })
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
        endpoints: List<com.itangcent.easyapi.exporter.model.ApiEndpoint>,
        outputConfig: OutputConfig = OutputConfig()
    ): com.itangcent.easyapi.exporter.model.ExportContext {
        return com.itangcent.easyapi.exporter.model.ExportContext(
            project = project,
            endpoints = endpoints,
            exportFormat = ExportFormat.POSTMAN,
            settings = com.itangcent.easyapi.settings.Settings(),
            outputConfig = outputConfig
        )
    }
}
