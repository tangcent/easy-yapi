package com.itangcent.easyapi.channel.yapi

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.registerServiceInstance
import com.intellij.testFramework.replaceService
import com.itangcent.easyapi.core.dashboard.ApiScanner
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.CallFailure
import com.itangcent.easyapi.core.export.EndpointScanResult
import com.itangcent.easyapi.core.export.ExportItemSummary
import com.itangcent.easyapi.core.export.ExportRejection
import com.itangcent.easyapi.core.export.ExportReport
import com.itangcent.easyapi.core.export.ExportTargets
import com.itangcent.easyapi.core.export.ExternalChangeSync
import com.itangcent.easyapi.core.export.FakeExternalChangeSync
import com.itangcent.easyapi.core.export.TargetedExportResult
import com.itangcent.easyapi.core.export.TargetedExportService
import com.itangcent.easyapi.core.http.HttpClientProvider
import com.itangcent.easyapi.core.ide.support.SelectionScope
import com.itangcent.easyapi.core.internal.threading.read
import com.itangcent.easyapi.core.settings.state.UnifiedAppSettingsState
import com.itangcent.easyapi.core.util.ide.ModuleHelper
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import com.itangcent.easyapi.testFramework.recordDialogs
import com.itangcent.easyapi.testFramework.updateConfig
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * The YApi channel's headless contract: what it declares, and the whole chain an external caller drives —
 * [TargetedExportService.export] → orchestrator → [YapiChannel] → [YapiExporter] → client → [FakeYapiServer] —
 * with no dialog.
 *
 * The sync of files changed outside the IDE is a [FakeExternalChangeSync]: the platform's waits for the EDT,
 * which this test holds while it waits for the export. Lives in the channel package because it names the YApi
 * channel (`core.export` tests stay channel-neutral).
 */
class YapiChannelTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var server: FakeYapiServer

    /** The YApi settings from before the test: they are application-wide, so they are put back in [tearDown]. */
    private var previousYapiSettings: Map<String, String>? = null

    override fun createConfigReader() = TestConfigReader.empty(project)

    override fun setUp() {
        super.setUp()
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
        previousYapiSettings = UnifiedAppSettingsState.getInstance().getState().modules[YAPI_SETTINGS]?.toMap()
        server = FakeYapiServer(mapOf(TOKEN to "11"))
        project.registerServiceInstance(
            HttpClientProvider::class.java,
            mock<HttpClientProvider> { on { getClient(anyOrNull(), anyOrNull(), anyOrNull()) } doReturn server }
        )
        project.replaceService(ExternalChangeSync::class.java, FakeExternalChangeSync(), testRootDisposable)
    }

    override fun tearDown() {
        try {
            val modules = UnifiedAppSettingsState.getInstance().getState().modules
            modules.remove(YAPI_SETTINGS)
            previousYapiSettings?.let { modules[YAPI_SETTINGS] = it.toMutableMap() }
            // Put the real provider back so the fake does not leak into later test classes.
            project.registerServiceInstance(HttpClientProvider::class.java, HttpClientProvider(project))
        } finally {
            super.tearDown()
        }
    }

    // ---- what the channel declares ----

    fun testDeclaresHeadlessSupport() {
        assertTrue(YapiChannel().supportsHeadless)
    }

    fun testAcceptsOneTokenOption() {
        val options = YapiChannel().exportOptions()

        assertEquals(listOf("token"), options.map { it.name })
        assertTrue("described for the caller", options.single().description.isNotBlank())
    }

    fun testTokenOptionBecomesTheSelectedToken() {
        assertEquals(YapiConfig(selectedToken = "t"), YapiChannel().buildConfig(mapOf("token" to "t")))
    }

    fun testWithoutTheTokenOptionTheSettingsDecide() {
        assertEquals(YapiConfig(), YapiChannel().buildConfig(emptyMap()))
    }

    // ---- the whole chain, headless ----

    fun testPassedTokenExportsEveryEndpointWithoutTouchingTheSettings() = runTest {
        useYapiSettings(tokens = "other-module=other-token")
        val endpoints = scanUserCtrl()

        val export = exportUserCtrl(mapOf("token" to TOKEN))

        val report = reportOf(export)
        assertReportedInOrder(endpoints, report.items)
        report.items.forEach { item ->
            assertEquals("a new API on an empty server: $item", "created", item.status)
            assertNull("the passed token decides the project", item.destination)
            assertTrue("links to the API: ${item.url}", item.url!!.startsWith("$SERVER/project/11/interface/api/"))
        }
        assertFalse("the result never holds the token", report.toJson().contains(TOKEN))
        assertEquals("a headless export must not show any dialog", emptyList<String>(), export.dialogs)
        assertEquals("the token table is left alone", "other-module=other-token", yapiSetting("yapiTokens"))
        assertEquals("every endpoint is saved", endpoints.size, server.count(SAVE))
    }

    fun testDestinationWithoutATokenFailsTheExportNamingIt() = runTest {
        useYapiSettings(tokens = "other-module=other-token")
        val endpoints = scanUserCtrl()
        val destination = destinationOf(endpoints.first())

        val export = exportUserCtrl()

        val failed = failureOf(export)
        assertTrue("nothing written: $failed", failed is ExportRejection.ExportFailed)
        val items = (failed as ExportRejection.ExportFailed).items
        assertReportedInOrder(endpoints, items)
        items.forEach { item ->
            assertEquals("failed", item.status)
            assertEquals(destination, item.destination)
            assertTrue("names the destination: ${item.reason}", item.reason!!.contains("'$destination'"))
        }
        assertEquals("a headless export must not show any dialog", emptyList<String>(), export.dialogs)
        assertEquals("nothing is saved", 0, server.count(SAVE))
    }

    fun testAlwaysAskOverwritesWithoutAsking() = runTest {
        useYapiSettings(tokens = null, mode = YapiExportMode.ALWAYS_ASK)
        val endpoints = scanUserCtrl()
        val options = mapOf("token" to TOKEN)
        val first = exportUserCtrl(options)
        val listedBefore = server.count(LIST_CAT)

        val second = exportUserCtrl(options)

        val report = reportOf(second)
        assertReportedInOrder(endpoints, report.items)
        report.items.forEach { assertEquals("already there, so overwritten: $it", "updated", it.status) }
        assertEquals("no dialog in either export", emptyList<String>(), first.dialogs + second.dialogs)
        assertEquals(
            "two folder listings per endpoint (the status check and the save), none to ask about the existing API",
            2 * endpoints.size,
            server.count(LIST_CAT) - listedBefore
        )
    }

    /** The export and the scan tool share one preparation: the same targets resolve and skip alike. */
    fun testExportReportsTheSameTargetsAndSkippedAsTheScan() = runTest {
        useYapiSettings(tokens = null)
        val targets = ExportTargets(classes = listOf(USER_CTRL, USER_INFO))

        val scanned = TargetedExportService.getInstance(project).scanEndpoints(targets)
        val report = reportOf(exportHeadless(targets, mapOf("token" to TOKEN)))

        assertTrue("expected Scanned, got $scanned", scanned is EndpointScanResult.Scanned)
        val listing = (scanned as EndpointScanResult.Scanned).report
        assertEquals("targets", listing.targets, report.targets)
        assertEquals("skipped", listing.skipped, report.skipped)
        assertEquals("the model is skipped", listOf(USER_INFO), report.skipped.map { it.input })
        assertEquals(
            "one item per listed endpoint, in order",
            listing.endpoints.map { it.methodName },
            report.items.map { it.methodName }
        )
    }

    fun testOnlyTheDestinationWithoutATokenFails() = runTest {
        val endpoints = scanUserCtrl()
        val module = destinationOf(endpoints.first())
        useYapiSettings(tokens = "$module=$TOKEN")
        updateConfig(project, "yapi.project" to """groovy:it.contextType() == "method" && it.name() == "getByCookie" ? "key-b" : null""")

        val report = reportOf(exportUserCtrl())

        assertReportedInOrder(endpoints, report.items)
        assertEquals("only the endpoint routed to key-b fails", 1, report.failed)
        val (failed, exported) = report.items.partition { it.methodName == "getByCookie" }
        failed.single().let { item ->
            assertEquals("failed", item.status)
            assertEquals("key-b", item.destination)
            assertTrue("names its destination: ${item.reason}", item.reason!!.contains("'key-b'"))
        }
        exported.forEach { item ->
            assertEquals("created", item.status)
            assertEquals(module, item.destination)
        }
    }

    // ---- helpers ----

    private class HeadlessExport(val result: TargetedExportResult, val dialogs: List<String>)

    /** Exports [targets] to YApi through the export service, recording every dialog shown. */
    private suspend fun exportHeadless(targets: ExportTargets, options: Map<String, String>): HeadlessExport {
        val (result, shown) = recordDialogs { TargetedExportService.getInstance(project).export(targets, "yapi", options) }
        return HeadlessExport(result, shown)
    }

    private suspend fun exportUserCtrl(options: Map<String, String> = emptyMap()): HeadlessExport =
        exportHeadless(ExportTargets(classes = listOf(USER_CTRL)), options)

    private fun reportOf(export: HeadlessExport): ExportReport {
        assertTrue("expected Exported, got ${export.result}", export.result is TargetedExportResult.Exported)
        return (export.result as TargetedExportResult.Exported).report
    }

    private fun failureOf(export: HeadlessExport): CallFailure {
        assertTrue("expected Failed, got ${export.result}", export.result is TargetedExportResult.Failed)
        return (export.result as TargetedExportResult.Failed).failure
    }

    private suspend fun scanUserCtrl(): List<ApiEndpoint> {
        val userCtrl = findClass(USER_CTRL)
        assertNotNull("UserCtrl should be loaded", userCtrl)
        val endpoints = ApiScanner.getInstance(project)
            .scanSelection(SelectionScope(listOf(userCtrl!!)), EmptyProgressIndicator())
        assertTrue("UserCtrl has endpoints", endpoints.isNotEmpty())
        return endpoints
    }

    /** Where the exporter routes [endpoint] without a `yapi.project` rule: its module, else the project. */
    private suspend fun destinationOf(endpoint: ApiEndpoint): String =
        endpoint.sourceClass?.let { ModuleHelper.resolveModuleName(it) } ?: project.name

    /** One item per scanned endpoint, in scan order: each from the method the endpoint comes from. */
    private suspend fun assertReportedInOrder(endpoints: List<ApiEndpoint>, items: List<ExportItemSummary>) {
        assertEquals("one item per endpoint: $items", endpoints.size, items.size)
        assertEquals(
            "in scan order",
            endpoints.map { read { it.sourceMethod?.name } },
            items.map { it.methodName }
        )
    }

    /** Saves the YApi settings through the binder, so its cache is dropped. */
    private fun useYapiSettings(tokens: String?, mode: YapiExportMode = YapiExportMode.ALWAYS_UPDATE) {
        settingBinder.save(YapiSettings(yapiServer = SERVER, yapiTokens = tokens, yapiExportMode = mode.name))
    }

    private fun yapiSetting(property: String): String? =
        UnifiedAppSettingsState.getInstance().getValue(YAPI_SETTINGS, property)

    private companion object {
        const val USER_CTRL = "com.itangcent.api.UserCtrl"
        const val USER_INFO = "com.itangcent.model.UserInfo"
        const val SERVER = "http://yapi.test"
        const val TOKEN = "tok-chain"
        const val YAPI_SETTINGS = "com.itangcent.easyapi.channel.yapi.YapiSettings"
        const val LIST_CAT = "/api/interface/list_cat"
        const val SAVE = "/api/interface/save"
    }
}
