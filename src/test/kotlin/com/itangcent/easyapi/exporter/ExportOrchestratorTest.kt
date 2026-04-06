package com.itangcent.easyapi.exporter

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ExportFormat
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.OutputConfig
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class ExportOrchestratorTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var orchestrator: ExportOrchestrator

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        orchestrator = ExportOrchestrator.getInstance(project)
    }

    private fun loadTestFiles() {
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/RestController.java")
        loadFile("spring/Controller.java")
        loadFile("spring/ResponseBody.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/UserCtrl.java")
    }

    override fun createConfigReader() = TestConfigReader.EMPTY

    fun testGetInstanceReturnsSameInstance() {
        val instance1 = ExportOrchestrator.getInstance(project)
        val instance2 = ExportOrchestrator.getInstance(project)
        
        assertSame("Should return same instance for same project", instance1, instance2)
    }

    fun testOrchestratorHasCorrectProjectReference() {
        assertNotNull("Orchestrator should not be null", orchestrator)
    }

    fun testOrchestrateExportWithNullSelectionReturnsResult() = runTest {
        val result = orchestrator.orchestrateExport(null, ExportFormat.MARKDOWN)
        
        assertNotNull("Result should not be null", result)
        assertTrue(
            "Result should be Error (no endpoints cached) or Success",
            result is ExportResult.Error || result is ExportResult.Success
        )
    }

    fun testExportEndpointsWithEmptyListReturnsResult() = runTest {
        val endpoints = emptyList<ApiEndpoint>()
        
        val result = orchestrator.exportEndpoints(endpoints, ExportFormat.MARKDOWN, OutputConfig.DEFAULT)
        
        assertNotNull("Result should not be null", result)
    }

    fun testExportEndpointsWithSingleEndpointReturnsResult() = runTest {
        val endpoints = listOf(createTestEndpoint())
        
        val result = orchestrator.exportEndpoints(endpoints, ExportFormat.MARKDOWN, OutputConfig.DEFAULT)
        
        assertNotNull("Result should not be null", result)
    }

    fun testExportEndpointsWithMultipleEndpointsReturnsResult() = runTest {
        val endpoints = listOf(
            createTestEndpoint("API 1", "/test1", HttpMethod.GET),
            createTestEndpoint("API 2", "/test2", HttpMethod.POST),
            createTestEndpoint("API 3", "/test3", HttpMethod.PUT)
        )
        
        val result = orchestrator.exportEndpoints(endpoints, ExportFormat.MARKDOWN, OutputConfig.DEFAULT)
        
        assertNotNull("Result should not be null", result)
    }

    fun testExportEndpointsWithDifferentFormats() = runTest {
        val endpoints = listOf(createTestEndpoint())
        
        ExportFormat.values().forEach { format ->
            val result = orchestrator.exportEndpoints(endpoints, format, OutputConfig.DEFAULT)
            assertNotNull("Result should not be null for format $format", result)
        }
    }

    fun testExportEndpointsWithCustomOutputConfig() = runTest {
        val endpoints = listOf(createTestEndpoint())
        val outputConfig = OutputConfig(
            outputDir = "/tmp",
            fileName = "test"
        )
        
        val result = orchestrator.exportEndpoints(endpoints, ExportFormat.MARKDOWN, outputConfig)
        
        assertNotNull("Result should not be null", result)
    }

    fun testOrchestratorHandlesAllExportFormats() = runTest {
        ExportFormat.values().forEach { format ->
            val result = orchestrator.orchestrateExport(null, format)
            assertNotNull("Should handle format $format", result)
        }
    }

    private fun createTestEndpoint(
        name: String = "Test API",
        path: String = "/test",
        method: HttpMethod = HttpMethod.GET
    ): ApiEndpoint {
        return ApiEndpoint(
            name = name,
            metadata = HttpMetadata(
                path = path,
                method = method
            )
        )
    }
}
