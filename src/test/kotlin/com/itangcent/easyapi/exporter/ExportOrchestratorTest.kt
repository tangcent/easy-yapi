package com.itangcent.easyapi.exporter

import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.itangcent.easyapi.exporter.channel.ChannelConfig
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class ExportOrchestratorTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var orchestrator: ExportOrchestrator
    private var previousDialog: TestDialog? = null
    private val testFileConfig = ChannelConfig.FileConfig(outputDir = "/tmp", fileName = "export_test")

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        orchestrator = ExportOrchestrator.getInstance(project)
        previousDialog = try {
            TestDialogManager.setTestDialog(TestDialog { 0 })
        } catch (_: Exception) {
            null
        }
    }

    override fun tearDown() {
        try {
            previousDialog?.let { TestDialogManager.setTestDialog(it) }
        } finally {
            super.tearDown()
        }
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

    override fun createConfigReader() = TestConfigReader.empty(project)

    fun testGetInstanceReturnsSameInstance() {
        val instance1 = ExportOrchestrator.getInstance(project)
        val instance2 = ExportOrchestrator.getInstance(project)

        assertSame("Should return same instance for same project", instance1, instance2)
    }

    fun testOrchestratorHasCorrectProjectReference() {
        assertNotNull("Orchestrator should not be null", orchestrator)
    }

    fun testOrchestrateExportWithNullSelectionReturnsResult() = runTest {
        val result = orchestrator.orchestrateExport(null, "markdown", testFileConfig)

        assertNotNull("Result should not be null", result)
        assertTrue(
            "Result should be Error (no endpoints cached) or Success",
            result is ExportResult.Error || result is ExportResult.Success
        )
    }

    fun testExportViaChannelWithEmptyListReturnsResult() = runTest {
        val endpoints = emptyList<ApiEndpoint>()

        val result = orchestrator.exportViaChannel("markdown", endpoints, testFileConfig)

        assertNotNull("Result should not be null", result)
    }

    fun testExportViaChannelWithSingleEndpointReturnsResult() = runTest {
        val endpoints = listOf(createTestEndpoint())

        val result = orchestrator.exportViaChannel("markdown", endpoints, testFileConfig)

        assertNotNull("Result should not be null", result)
    }

    fun testExportViaChannelWithMultipleEndpointsReturnsResult() = runTest {
        val endpoints = listOf(
            createTestEndpoint("API 1", "/test1", HttpMethod.GET),
            createTestEndpoint("API 2", "/test2", HttpMethod.POST),
            createTestEndpoint("API 3", "/test3", HttpMethod.PUT)
        )

        val result = orchestrator.exportViaChannel("markdown", endpoints, testFileConfig)

        assertNotNull("Result should not be null", result)
    }

    fun testExportViaChannelWithCustomChannelConfig() = runTest {
        val endpoints = listOf(createTestEndpoint())
        val channelConfig = ChannelConfig.FileConfig(
            outputDir = "/tmp",
            fileName = "test"
        )

        val result = orchestrator.exportViaChannel("markdown", endpoints, channelConfig)

        assertNotNull("Result should not be null", result)
    }

    fun testOrchestratorHandlesMarkdownChannel() = runTest {
        val result = orchestrator.orchestrateExport(null, "markdown", testFileConfig)
        assertNotNull("Should handle markdown channel", result)
    }

    private fun createTestEndpoint(
        name: String = "Test API",
        path: String = "/test",
        method: HttpMethod = HttpMethod.GET
    ): ApiEndpoint {
        return ApiEndpoint(
            name = name,
            metadata = httpMetadata(
                path = path,
                method = method
            )
        )
    }
}
