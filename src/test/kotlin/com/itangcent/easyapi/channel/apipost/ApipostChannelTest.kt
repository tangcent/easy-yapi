package com.itangcent.easyapi.channel.apipost

import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.channel.spi.ChannelConfig
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.ExportContext
import com.itangcent.easyapi.core.export.ExportResult
import com.itangcent.easyapi.core.export.GrpcMetadata
import com.itangcent.easyapi.core.export.GrpcStreamingType
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.update
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File

/**
 * Tests for [ApipostChannel].
 *
 * Covers:
 *  - Channel metadata contract (task 21.1).
 *  - gRPC filtering and the empty-input error (task 21.2).
 *  - **File fallback** (task 21.7 / AC-3): no token ⇒ `Success` with
 *    `fileExport=true`, and `handleResult` writes `apipost.json`.
 *  - **Push dedup** (task 21.3/21.4 / AC-6): an API already on the server is
 *    updated with its `target_id` instead of duplicated.
 *  - **Parent resolution** (corrected by `REVIEW.md §6.9.3`): no pushed API points
 *    at a folder that was not pushed in the same run.
 *  - **Push failure** (task 21.5): a `Failure` short-circuits to
 *    `ExportResult.Error`.
 *
 * The push tests inject a [FakeApipostApiClient] through
 * [ApipostChannel.apiClientFactory]. The ordering and `$ref` rules live in
 * [ApipostExporterTest], which drives the exporter with a hand-built document —
 * necessary because the OpenAPI path produces no folders without real tags, so
 * the folder behaviour cannot be exercised from here.
 */
class ApipostChannelTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var channel: ApipostChannel
    private var previousDialog: TestDialog? = null

    override fun setUp() {
        super.setUp()
        channel = ApipostChannel()
        previousDialog = try {
            TestDialogManager.setTestDialog(TestDialog { 0 })
        } catch (_: Exception) {
            null
        }
        SettingBinder.getInstance(project).update(ApipostSettings::class) {
            apipostServer = ApipostSettings.APIPOST_OPEN_HOST
            apipostToken = null
            apipostProjectId = null
        }
    }

    override fun tearDown() {
        try {
            previousDialog?.let { TestDialogManager.setTestDialog(it) }
        } finally {
            super.tearDown()
        }
    }

    // ─── Metadata contract (task 21.1) ──────────────────────────────────

    fun testChannelMetadata() {
        assertEquals("apipost", channel.id)
        assertEquals("ApiPost (Beta)", channel.displayName)
        assertEquals(false, channel.enabledByDefault)
        assertEquals(true, channel.beta)
        assertEquals(false, channel.supportsGrpc)
        assertEquals(true, channel.exposeAsAction)
        assertEquals("Export to ApiPost", channel.actionText)
        assertEquals(ApipostSettings::class, channel.settingsType)
        assertEquals(listOf("apipost"), channel.configFiles())
        assertEquals("should contribute 6 rule keys", 6, channel.ruleKeys().size)
    }

    fun testEmptyInputIsError() {
        val result = exportWith(emptyList())
        assertTrue("empty input should be an Error, got $result", result is ExportResult.Error)
        assertEquals("No HTTP endpoints to export", (result as ExportResult.Error).message)
    }

    fun testGrpcOnlyInputIsError() {
        val result = exportWith(listOf(grpcEndpoint()))
        assertTrue("gRPC-only should be an Error, got $result", result is ExportResult.Error)
    }

    fun testMixedInputCountsOnlyHttp() {
        val result = exportWith(listOf(httpEndpoint(path = "/a", method = HttpMethod.GET), grpcEndpoint()))
        assertTrue("mixed input should succeed, got $result", result is ExportResult.Success)
        assertEquals(1, (result as ExportResult.Success).count)
    }

    // ─── File fallback (task 21.7 / AC-3) ───────────────────────────────

    fun testNoTokenDegradesToFileExport() {
        val result = exportWith(listOf(httpEndpoint(path = "/a", method = HttpMethod.GET)))
        assertTrue(result is ExportResult.Success)
        val metadata = (result as ExportResult.Success).metadata as ApipostExportMetadata
        assertTrue("should be flagged as a file export", metadata.fileExport)
        assertEquals(0, metadata.created)
        assertEquals(0, metadata.updated)
        assertTrue(
            "native envelope should carry import_type",
            metadata.content.contains("\"import_type\""),
        )
        assertTrue(
            "envelope should be the ApiPost native format, not OpenAPI",
            metadata.content.contains("\"import_type\":\"apipost\""),
        )
    }

    fun testHandleResultWritesFileAndReturnsTrue() {
        val result = exportWith(listOf(httpEndpoint(path = "/a", method = HttpMethod.GET)))
        val dir = createTempDir()
        val handled = runBlocking {
            channel.handleResult(
                project,
                result as ExportResult.Success,
                ChannelConfig.FileConfig(outputDir = dir.absolutePath, fileName = "apipost"),
            )
        }
        assertTrue("handleResult should report handled", handled)
        val file = File(dir, "apipost.json")
        assertTrue("expected ${file.absolutePath} to exist", file.exists())
        assertTrue(file.readText().contains("\"import_type\""))
    }

    fun testHandleResultReturnsFalseForForeignMetadata() {
        val success = ExportResult.Success(count = 1, target = "Other", metadata = null)
        val handled = runBlocking { channel.handleResult(project, success, ChannelConfig.Empty) }
        assertFalse("foreign metadata should not be handled", handled)
    }

    // ─── Push (tasks 21.3 / 21.4 / AC-6) ────────────────────────────────

    fun testPushCreatesWhenNothingExists() {
        val client = FakeApipostApiClient()
        channel.apiClientFactory = ApipostApiClientFactory { _, _, _ -> client }

        val result = exportWith(
            listOf(httpEndpoint(path = "/users", method = HttpMethod.GET)),
            token = "tok",
            projectId = "p1",
        )
        assertTrue("push should succeed, got $result", result is ExportResult.Success)
        val metadata = (result as ExportResult.Success).metadata as ApipostExportMetadata
        assertFalse("pushed export is not a file export", metadata.fileExport)
        assertEquals(1, metadata.created)
        assertEquals(0, metadata.updated)
        assertEquals(1, client.createdApis.size)
        assertTrue("no update should have been issued", client.updatedApis.isEmpty())
    }

    fun testPushUpdatesExistingApiInsteadOfDuplicating() {
        val client = FakeApipostApiClient(
            existingApis = listOf(
                ApipostExistingApi(
                    targetId = "server-id-1",
                    name = "List users",
                    method = "GET",
                    path = "/users",
                ),
            ),
        )
        channel.apiClientFactory = ApipostApiClientFactory { _, _, _ -> client }

        val result = exportWith(
            listOf(httpEndpoint(path = "/users", method = HttpMethod.GET)),
            token = "tok",
            projectId = "p1",
        )
        assertTrue(result is ExportResult.Success)
        val metadata = (result as ExportResult.Success).metadata as ApipostExportMetadata
        assertEquals(0, metadata.created)
        assertEquals(1, metadata.updated)
        assertEquals(
            "update must carry the server's target_id",
            listOf("server-id-1"),
            client.updatedApis.keys.toList(),
        )
        assertTrue("nothing should have been created", client.createdApis.isEmpty())
    }

    fun testEveryPushedApiResolvesItsParentWithinTheSameRun() {
        val client = FakeApipostApiClient()
        channel.apiClientFactory = ApipostApiClientFactory { _, _, _ -> client }

        exportWith(
            listOf(httpEndpoint(path = "/users", method = HttpMethod.GET)),
            token = "tok",
            projectId = "p1",
        )

        // Folders are created through the *same* endpoint as APIs, with
        // `target_type=folder` (REVIEW.md §6.9.3) — the separate-route reading that
        // produced the old "flatten everything to the root" behaviour was wrong. What
        // must hold at this level is that a parent a pushed node carries is a folder
        // pushed in this same run, because anything else answers `14002 父节点已删除`.
        //
        // The folder-specific cases (creation order, reuse by name, nesting) live in
        // ApipostExporterTest: this fixture's document has no tags, so the OpenAPI
        // path cannot produce a folder here at all.
        val pushedFolderIds = client.createdFolders.map { it.targetId }.toSet()
        assertTrue(
            "this fixture should push no folder",
            pushedFolderIds.isEmpty(),
        )
        client.createdApis.forEach { node ->
            assertTrue(
                "api '${node.name}' points at '${node.parentId}', which is neither the root " +
                    "nor a folder pushed in this run",
                node.parentId == ApipostImportConfig.ROOT_ID || node.parentId in pushedFolderIds,
            )
        }
    }

    fun testPushFailureBecomesExportError() {
        val client = FakeApipostApiClient(
            apiFailure = ApipostSyncResult.Failure("api_token错误或已被删除！", ApipostCodes.TOKEN_INVALID),
        )
        channel.apiClientFactory = ApipostApiClientFactory { _, _, _ -> client }

        val result = exportWith(
            listOf(httpEndpoint(path = "/users", method = HttpMethod.GET)),
            token = "tok",
            projectId = "p1",
        )
        assertTrue("failure should surface as Error, got $result", result is ExportResult.Error)
        assertTrue((result as ExportResult.Error).message.contains("api_token错误"))
    }

    fun testMissingProjectIdDegradesToFileEvenWithToken() {
        val result = exportWith(
            listOf(httpEndpoint(path = "/users", method = HttpMethod.GET)),
            token = "tok",
            projectId = null,
        )
        val metadata = (result as ExportResult.Success).metadata as ApipostExportMetadata
        assertTrue("no project id ⇒ file export", metadata.fileExport)
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private fun exportWith(
        endpoints: List<ApiEndpoint>,
        token: String? = null,
        projectId: String? = null,
    ): ExportResult = runBlocking {
        channel.export(
            ExportContext(
                project = project,
                endpoints = endpoints,
                channelId = "apipost",
                channelConfig = ApipostConfig(selectedToken = token, projectId = projectId),
            ),
        )
    }

    private fun httpEndpoint(
        name: String? = null,
        path: String,
        method: HttpMethod,
        methodName: String = "endpoint",
    ): ApiEndpoint {
        val psiMethod = mock<PsiMethod> {
            on { this.name } doReturn methodName
        }
        return ApiEndpoint(
            name = name,
            metadata = httpMetadata(path = path, method = method),
            sourceMethod = psiMethod,
        )
    }

    private fun grpcEndpoint(): ApiEndpoint = ApiEndpoint(
        name = "SayHello",
        metadata = GrpcMetadata(
            path = "/helloworld.Greeter/SayHello",
            serviceName = "Greeter",
            methodName = "SayHello",
            packageName = "helloworld",
            streamingType = GrpcStreamingType.UNARY,
        ),
    )
}
