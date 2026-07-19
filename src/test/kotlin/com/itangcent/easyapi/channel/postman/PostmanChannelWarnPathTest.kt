package com.itangcent.easyapi.channel.postman

import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.registerServiceInstance
import com.itangcent.easyapi.channel.spi.ChannelConfig
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.ExportResult
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.ExportOrchestrator
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.http.HttpClient
import com.itangcent.easyapi.core.http.HttpClientProvider
import com.itangcent.easyapi.core.http.HttpRequest
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Tests the warn-path branch in [ExportOrchestrator.exportViaChannel] — i.e.
 * the `else if (result is ExportResult.Error) { LOG.warn(...) }` branch.
 *
 * Co-located in `channel/postman/` because the trigger is Postman-specific:
 * a fake `PostmanSettings.postmanToken` forces the upload path, and a mock
 * `HttpClientProvider` returns a client whose `execute()` throws —
 * `PostmanApiClient.uploadCollection` catches the exception and returns
 * `UploadResult(success=false)`, causing `PostmanChannel.export` to return
 * `ExportResult.Error`.
 *
 * Moved here from `core/export/ExportOrchestratorTest.kt` to keep
 * `core.export.*` free of `channel.<id>.*` imports (Decision CO3 DAG rule).
 */
class PostmanChannelWarnPathTest : EasyApiLightCodeInsightFixtureTestCase() {

    private var previousDialog: TestDialog? = null

    override fun setUp() {
        super.setUp()
        previousDialog = try {
            TestDialogManager.setTestDialog(TestDialog { 0 })
        } catch (_: Exception) {
            null
        }
    }

    override fun tearDown() {
        try {
            previousDialog?.let { TestDialogManager.setTestDialog(it) }
            // Restore HttpClientProvider so the mock registered below does not
            // leak into subsequent test classes (e.g. MarkdownChannelTemplateTest,
            // which uses a real HttpClientProvider to fetch remote templates).
            project.registerServiceInstance(
                HttpClientProvider::class.java,
                HttpClientProvider(project)
            )
        } finally {
            super.tearDown()
        }
    }

    fun testExportViaChannel_logsWarnWhenChannelExportReturnsError() = runTest {
        val orchestrator = ExportOrchestrator.getInstance(project)
        val binder = SettingBinder.getInstance(project)
        val originalGeneral = binder.read(GeneralSettings::class)
        val originalPostman = binder.read(PostmanSettings::class)
        try {
            // Set a fake Postman token so the upload path is taken (not mock mode).
            binder.save(PostmanSettings(postmanToken = "fake-token-for-test"))

            // Replace HttpClientProvider with a mock whose getClient() returns a
            // failing HttpClient. PostmanApiClient.uploadCollection catches the
            // exception and returns UploadResult(success=false).
            val failingClient = object : HttpClient {
                override suspend fun execute(request: HttpRequest) =
                    throw java.net.ConnectException("Connection refused (test mock)")
                override fun close() {}
            }
            val mockProvider = mock<HttpClientProvider> {
                on { getClient(anyOrNull(), anyOrNull(), anyOrNull()) } doReturn failingClient
            }
            project.registerServiceInstance(HttpClientProvider::class.java, mockProvider)

            val endpoints = listOf(createTestEndpoint())
            val result = orchestrator.exportViaChannel("postman", endpoints, ChannelConfig.Empty)

            assertNotNull(result)
            assertTrue(
                "Postman export with a failing HTTP client should return Error. Got: $result",
                result is ExportResult.Error
            )
        } finally {
            binder.save(originalGeneral)
            binder.save(originalPostman)
        }
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
