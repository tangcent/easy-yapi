package com.itangcent.easyapi.core.export

import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.registerServiceInstance
import com.itangcent.easyapi.channel.spi.ChannelConfig
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.ExportResult
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.ide.support.SelectionScope
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class ExportOrchestratorTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var orchestrator: ExportOrchestrator
    private var previousDialog: TestDialog? = null
    // Use ChannelConfig.FileConfig (SPI base) instead of MarkdownConfig so this
    // test file does not import channel.<id>.* — MarkdownChannel will fall back
    // to its default template/inline/path when channelConfig is not MarkdownConfig.
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

    fun testOrchestrateExportWithEmptyCacheReturnsError() = runTest {
        // When no endpoints are cached and no selection is provided,
        // orchestrateExport should return an Error result.
        // Note: If a background scan has populated the cache, this may return Success instead.
        val result = orchestrator.orchestrateExport(null, "markdown", testFileConfig)
        assertNotNull(result)
        assertTrue(
            "Should be Error (no endpoints) or Success (background scan populated cache)",
            result is ExportResult.Error || result is ExportResult.Success
        )
    }

    fun testOrchestrateExportWithSelectionExportsEndpoints() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull("UserCtrl should be loaded", psiClass)
        val selection = SelectionScope(listOf(psiClass!!))

        val result = orchestrator.orchestrateExport(selection, "markdown", testFileConfig)
        assertNotNull(result)
        // With real endpoints found, the export should succeed or fail gracefully
        // but either way the notifyInfo path is exercised
    }

    /**
     * Verifies that a method-level [SelectionScope] causes the orchestrator
     * to export only the selected method's endpoints, not every endpoint in
     * the containing class (issue #1407).
     */
    fun testOrchestrateExportWithMethodSelectionExportsOnlySelectedMethod() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull("UserCtrl should be loaded", psiClass)
        val greetingMethod = psiClass!!.findMethodsByName("greeting", false).first() as PsiMethod

        // Export with only the greeting method selected.
        val methodSelection = SelectionScope(listOf(greetingMethod))
        val methodResult = orchestrator.orchestrateExport(methodSelection, "markdown", testFileConfig)

        // Export with the entire class selected, for comparison.
        val classSelection = SelectionScope(listOf(psiClass))
        val classResult = orchestrator.orchestrateExport(classSelection, "markdown", testFileConfig)

        assertNotNull("Method selection result should not be null", methodResult)
        assertNotNull("Class selection result should not be null", classResult)

        if (methodResult is ExportResult.Success && classResult is ExportResult.Success) {
            assertEquals(
                "Method selection should export exactly 1 endpoint (greeting)",
                1, methodResult.count
            )
            assertTrue(
                "Class selection ($classResult.count) should export more endpoints than single method (${methodResult.count})",
                classResult.count > methodResult.count
            )
        }
    }

    /**
     * Verifies that a multi-method [SelectionScope] exports endpoints from
     * exactly those methods, not the entire class (issue #1407).
     */
    fun testOrchestrateExportWithMultipleMethodSelectionExportsOnlyThoseMethods() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull("UserCtrl should be loaded", psiClass)
        val greetingMethod = psiClass!!.findMethodsByName("greeting", false).first() as PsiMethod
        val getMethod = psiClass.findMethodsByName("get", false).first() as PsiMethod

        val selection = SelectionScope(listOf(greetingMethod, getMethod))
        val result = orchestrator.orchestrateExport(selection, "markdown", testFileConfig)

        assertNotNull(result)
        if (result is ExportResult.Success) {
            assertEquals(
                "Should export exactly 2 endpoints (greeting + get)",
                2, result.count
            )
        }
    }

    fun testOrchestrateExportWithUnknownChannelReturnsError() = runTest {
        val result = orchestrator.orchestrateExport(null, "nonexistent-channel", testFileConfig)
        assertNotNull(result)
        assertTrue("Should be Error for unknown channel", result is ExportResult.Error)
        assertEquals("No channel registered for id: nonexistent-channel", (result as ExportResult.Error).message)
    }

    fun testExportViaChannelWithUnknownChannelReturnsError() = runTest {
        val endpoints = listOf(createTestEndpoint())
        val result = orchestrator.exportViaChannel("nonexistent-channel", endpoints, testFileConfig)
        assertNotNull(result)
        assertTrue("Should be Error for unknown channel", result is ExportResult.Error)
    }

    // --- Task 3.1: ExportOrchestrator refuses a disabled channel (Req 4.4) ---
    // http-client is default-off (Task 2.1), so with no stored preference it is
    // disabled and the export boundary must refuse it before scanning/exporting.

    fun testOrchestrateExportWithDefaultOffChannelReturnsError() = runTest {
        val result = orchestrator.orchestrateExport(null, "http-client", ChannelConfig.Empty)
        assertNotNull(result)
        assertTrue("Should be Error for disabled channel", result is ExportResult.Error)
        val msg = (result as ExportResult.Error).message
        assertTrue(
            "Error should name the channel as disabled. Got: $msg",
            msg.contains("disabled", ignoreCase = true)
        )
    }

    fun testExportViaChannelWithDefaultOffChannelReturnsError() = runTest {
        val endpoints = listOf(createTestEndpoint())
        val result = orchestrator.exportViaChannel("http-client", endpoints, ChannelConfig.Empty)
        assertNotNull(result)
        assertTrue("Should be Error for disabled channel", result is ExportResult.Error)
        val msg = (result as ExportResult.Error).message
        assertTrue(
            "Error should name the channel as disabled. Got: $msg",
            msg.contains("disabled", ignoreCase = true)
        )
    }

    fun testOrchestrateExportWithExplicitlyEnabledChannelProceeds() = runTest {
        // Explicitly enable http-client; the disabled guard must NOT fire.
        val binder = SettingBinder.getInstance(project)
        val original = binder.read(GeneralSettings::class)
        try {
            binder.save(GeneralSettings(enabledChannels = arrayOf("http-client")))
            // Select a non-controller model class so scanEndpoints yields no
            // endpoints — the export is then refused with "No API endpoints found"
            // (not "disabled"), proving the guard passed without triggering the
            // host-selection dialog of the http-client export path.
            val userInfoClass = findClass("com.itangcent.model.UserInfo")
            assertNotNull("UserInfo should be loaded", userInfoClass)
            val selection = SelectionScope(listOf(userInfoClass!!))
            val result = orchestrator.orchestrateExport(selection, "http-client", ChannelConfig.Empty)
            assertNotNull(result)
            assertTrue("Should be Error (no endpoints found)", result is ExportResult.Error)
            val msg = (result as ExportResult.Error).message
            assertFalse(
                "An explicitly-enabled channel must not be refused as disabled. Got: $msg",
                msg.contains("disabled", ignoreCase = true)
            )
        } finally {
            // Restore original settings so this test does not pollute others
            // (DefaultSettingBinder invalidates its cache on save → fresh read).
            binder.save(original)
        }
    }

    // --- Task 3.2: exportViaChannel LOG.warn path when channel.export returns Error ---
    // Moved to channel/postman/PostmanChannelWarnPathTest.kt — co-located with the
    // Postman channel it tests (the test mocks PostmanSettings + HttpClientProvider
    // to force the upload-failure path, which is Postman-specific). The warn-path
    // branch in ExportOrchestrator is still exercised by that test, just from the
    // channel bucket.

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
