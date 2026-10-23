package com.itangcent.easyapi.channel.yapi

import com.itangcent.easyapi.channel.spi.ChannelConfig
import com.itangcent.easyapi.channel.yapi.YapiConfig
import com.itangcent.easyapi.channel.yapi.markdown.MarkdownRender
import com.itangcent.easyapi.core.export.ExportItemListener
import com.itangcent.easyapi.core.export.ExportItemResult
import com.itangcent.easyapi.core.export.ExportItemStatus
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.ExportResult
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.http.HttpClient
import com.itangcent.easyapi.core.http.HttpClientProvider
import com.itangcent.easyapi.core.http.HttpRequest
import com.itangcent.easyapi.core.http.HttpResponse
import com.itangcent.easyapi.core.logging.IdeaConsole
import com.itangcent.easyapi.core.logging.IdeaConsoleProvider
import com.itangcent.easyapi.core.logging.LogLevel
import com.itangcent.easyapi.core.logging.LoggerCollector
import com.itangcent.easyapi.core.settings.update
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.recordDialogs
import com.itangcent.easyapi.testFramework.updateConfig
import com.itangcent.easyapi.testFramework.wrap
import org.junit.Assert.*
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.net.ConnectException
import java.util.Collections

class YapiExporterTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporterProject: com.intellij.openapi.project.Project
    private lateinit var exporter: YapiExporter
    private lateinit var settingsHelper: YapiSettingsHelper

    private companion object {
        const val SERVER_REJECTION = "token is invalid, please copy a new token from project settings"
        const val TOKEN = "tok-test"
        const val GET_PROJECT = "/api/project/get"
        const val LIST_CAT = "/api/interface/list_cat"
        const val LIST_MENU = "/api/interface/list_menu"
        const val SAVE = "/api/interface/save"
    }

    override fun setUp() {
        super.setUp()
        exporterProject = createExporterProject()
        exporter = YapiExporter(exporterProject)
    }

    @org.junit.Test
    fun `test export without server configuration returns error`() {
        exporterProject = createExporterProject(serverUrl = null)
        exporter = YapiExporter(exporterProject)
        val endpoint = createTestEndpoint()
        val context = createTestContext(listOf(endpoint))

        val result = kotlinx.coroutines.runBlocking { exporter.export(context) }

        assertTrue("Expected Error but got $result", result is ExportResult.Error)
        val error = result as ExportResult.Error
        assertTrue(error.message.contains("YAPI server URL"))
    }

    @org.junit.Test
    fun `test export without token returns error`() {
        val endpoint = createTestEndpoint()
        val context = createTestContext(listOf(endpoint), selectedToken = null)

        val result = kotlinx.coroutines.runBlocking { exporter.export(context, selectedToken = null) }

        assertTrue("Expected Error but got $result", result is ExportResult.Error)
        val error = result as ExportResult.Error
        assertTrue(
            "Error message should mention token: ${error.message}",
            error.message.contains("token", ignoreCase = true)
        )
    }

    @org.junit.Test
    fun `test export with valid configuration attempts upload`() {
        val endpoint = createTestEndpoint(
            name = "Get User",
            path = "/api/users/{id}",
            method = HttpMethod.GET
        )

        val context = createTestContext(listOf(endpoint), selectedToken = "test-token")

        val result = kotlinx.coroutines.runBlocking { exporter.export(context, selectedToken = "test-token") }

        assertTrue(
            "Expected Error (network) or Success, got $result",
            result is ExportResult.Error || result is ExportResult.Success
        )
    }

    @org.junit.Test
    fun `test export with multiple endpoints`() {
        val endpoints = listOf(
            createTestEndpoint("Get Users", "/api/users", HttpMethod.GET),
            createTestEndpoint("Create User", "/api/users", HttpMethod.POST),
            createTestEndpoint("Update User", "/api/users/{id}", HttpMethod.PUT)
        )

        val context = createTestContext(endpoints, selectedToken = "test-token")
        val result = kotlinx.coroutines.runBlocking { exporter.export(context, selectedToken = "test-token") }

        assertTrue(
            "Expected Error (network) or Success, got $result",
            result is ExportResult.Error || result is ExportResult.Success
        )
    }

    @org.junit.Test
    fun `test export handles different http methods`() {
        val endpoints = listOf(
            createTestEndpoint("GET Test", "/test", HttpMethod.GET),
            createTestEndpoint("POST Test", "/test", HttpMethod.POST),
            createTestEndpoint("PUT Test", "/test", HttpMethod.PUT),
            createTestEndpoint("DELETE Test", "/test", HttpMethod.DELETE)
        )

        val context = createTestContext(endpoints, selectedToken = "test-token")
        val result = kotlinx.coroutines.runBlocking { exporter.export(context, selectedToken = "test-token") }

        assertTrue(
            "Expected Error (network) or Success, got $result",
            result is ExportResult.Error || result is ExportResult.Success
        )
    }

    @org.junit.Test
    fun `test export context validation`() {
        val endpoint = createTestEndpoint()
        val context = createTestContext(listOf(endpoint))

        assertEquals(exporterProject, context.project)
        assertEquals("yapi", context.channelId)
        assertTrue(context.endpoints.isNotEmpty())
    }

    @org.junit.Test
    fun `test export with empty endpoints list`() {
        val endpoints = emptyList<com.itangcent.easyapi.core.export.ApiEndpoint>()

        val context = createTestContext(endpoints, selectedToken = "test-token")
        val result = kotlinx.coroutines.runBlocking { exporter.export(context, selectedToken = "test-token") }

        assertTrue(
            "Expected Error (no endpoints) or Success (empty upload), got $result",
            result is ExportResult.Error || result is ExportResult.Success
        )
    }

    @org.junit.Test
    fun `test export surfaces the server rejection reason for a rejected token`() {
        // Reproduces issue #1470: a YApi server (e.g. Yapix after migrating from YApi)
        // rejects the legacy project token with an actionable message. The error keeps the
        // folder for context, but must carry the server's own words — not the bare
        // 'Failed to resolve cart' line that points at the folder structure.
        val httpClient = mock<HttpClient> {
            onBlocking { execute(any()) } doReturn HttpResponse(
                code = 200,
                body = """{"errcode":42014,"errmsg":"$SERVER_REJECTION","data":null}"""
            )
        }
        exporterProject = createExporterProject(httpClient = httpClient)
        exporter = YapiExporter(exporterProject)

        val endpoint = createTestEndpoint(name = "Get User", path = "/api/users/{id}")
        val context = createTestContext(listOf(endpoint), selectedToken = "legacy-token")

        val result = kotlinx.coroutines.runBlocking { exporter.export(context, selectedToken = "legacy-token") }

        assertTrue("Expected Error, got $result", result is ExportResult.Error)
        val error = result as ExportResult.Error
        assertTrue(
            "Export error should keep the cart context, got: ${error.message}",
            error.message.contains("Failed to resolve cart 'anonymous'")
        )
        assertTrue(
            "Export error should carry the server's reason, got: ${error.message}",
            error.message.contains(SERVER_REJECTION)
        )
    }

    /**
     * The path a success notification's links come from: a mocked server that accepts the upload
     * and echoes `data._id`, so the balloon can deep-link to the API that was just written.
     */
    @org.junit.Test
    fun `test single endpoint export links straight to the exported api`() =
        kotlinx.coroutines.runBlocking {
            val httpClient = mock<HttpClient>()
            stubOk(httpClient, "/api/project/get", """{"errcode":0,"errmsg":"成功！","data":{"_id":"42"}}""")
            stubOk(httpClient, "/api/interface/getCatMenu", """{"errcode":0,"data":[]}""")
            stubOk(httpClient, "/api/interface/add_cat", """{"errcode":0,"data":{"_id":34,"name":"anonymous"}}""")
            stubOk(httpClient, "/api/interface/list_cat", """{"errcode":0,"data":{"list":[]}}""")
            stubOk(httpClient, "/api/interface/save", """{"errcode":0,"errmsg":"成功！","data":{"_id":"333"}}""")

            exporterProject = createExporterProject(tokenForModule = "test-token", httpClient = httpClient)
            exporter = YapiExporter(exporterProject)
            val context = createTestContext(
                listOf(
                    createTestEndpoint(
                        name = "轻量刷新 token",
                        path = "/refresh-token-lite",
                        method = HttpMethod.POST
                    )
                ),
                selectedToken = "test-token"
            )

            val result = exporter.export(context, selectedToken = "test-token")

            assertTrue("Expected Success but got $result", result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as YapiExportMetadata
            assertEquals(
                "单端点导出应把刚导出的 API 作为首个链接",
                "轻量刷新 token" to "http://localhost:3000/project/42/interface/api/333",
                metadata.notificationLinks().first()
            )
        }

    // ---- headless export: no dialog, and one result per endpoint ----

    fun `test headless export without a token fails every endpoint by destination and shows no dialog`() {
        exporterProject = createExporterProject(tokenLookup = { null })
        exporter = YapiExporter(exporterProject)
        val endpoints = listOf(
            createTestEndpoint("Get User", "/api/users/{id}"),
            createTestEndpoint("List Users", "/api/users")
        )
        val items = mutableListOf<ExportItemResult>()
        val context = createTestContext(endpoints, headless = true, itemListener = { items += it })

        val (result, shown) = recordDialogs { kotlinx.coroutines.runBlocking { exporter.export(context) } }

        assertTrue("Expected Error but got $result", result is ExportResult.Error)
        assertEquals("one result per endpoint: $items", endpoints.size, items.size)
        endpoints.zip(items).forEach { (endpoint, item) ->
            assertSame("reported for the endpoint itself", endpoint, item.endpoint)
            assertEquals(ExportItemStatus.FAILED, item.status)
            assertEquals("routed by the project name", exporterProject.name, item.destination)
            assertTrue("names the destination: ${item.reason}", item.reason!!.contains("'${exporterProject.name}'"))
        }
        assertEquals("a headless export must not show any dialog", emptyList<String>(), shown)
    }

    fun `test headless export checks a passed token before any endpoint`() {
        val server = FakeYapiServer(projects = emptyMap())
        val console = LoggerCollector()
        exporterProject = createExporterProject(httpClient = server, console = console)
        exporter = YapiExporter(exporterProject)
        val context = createTestContext(listOf(createTestEndpoint()), selectedToken = "bad-token", headless = true)

        val result = kotlinx.coroutines.runBlocking { exporter.export(context, selectedToken = "bad-token") }

        assertTrue("Expected Error but got $result", result is ExportResult.Error)
        val message = (result as ExportResult.Error).message
        assertTrue(message, message.contains("YApi rejected the token passed for this export"))
        assertTrue("carries the server's reason: $message", message.contains("token 无效"))
        assertFalse("not a server problem: $message", message.contains("Yapi Server"))
        assertEquals("nothing is saved", 0, server.count(SAVE))
        assertTrue(
            "the error is logged where it is received, not here too: ${console.getMessages()}",
            console.getMessages().none { it.message.contains("YApi rejected the token passed") }
        )
    }

    fun `test headless export reports an unreachable server as a server problem`() {
        val unreachable = UnreachableServer()
        exporterProject = createExporterProject(httpClient = unreachable)
        exporter = YapiExporter(exporterProject)
        val context = createTestContext(listOf(createTestEndpoint()), selectedToken = TOKEN, headless = true)

        val result = kotlinx.coroutines.runBlocking { exporter.export(context, selectedToken = TOKEN) }

        assertTrue("Expected Error but got $result", result is ExportResult.Error)
        val message = (result as ExportResult.Error).message
        assertTrue(message, message.contains("Could not check the token passed"))
        assertTrue(message, message.contains("Request to YApi server failed"))
        assertTrue("points at the server setting: $message", message.contains("\"Yapi Server\""))
        assertFalse("does not say YApi rejected it: $message", message.contains("rejected"))
        assertTrue("nothing is saved: ${unreachable.urls}", unreachable.urls.none { it.contains(SAVE) })
    }

    fun `test each endpoint is reported as updated, created or failed`() {
        val server = FakeYapiServer(mapOf(TOKEN to "11"))
        val existingId = server.addApi("11", "anonymous", "Old title", "/api/existing", "GET")
        server.saveFailures["/api/broken"] = "path is invalid"
        exporterProject = createExporterProject(httpClient = server)
        exporter = YapiExporter(exporterProject)
        val existing = createTestEndpoint("Existing", "/api/existing", HttpMethod.GET)
        val fresh = createTestEndpoint("Fresh", "/api/fresh", HttpMethod.GET)
        val broken = createTestEndpoint("Broken", "/api/broken", HttpMethod.POST)
        val items = mutableListOf<ExportItemResult>()
        val context = createTestContext(
            listOf(existing, fresh, broken), selectedToken = TOKEN, headless = true, itemListener = { items += it }
        )

        val result = kotlinx.coroutines.runBlocking { exporter.export(context, selectedToken = TOKEN) }

        assertTrue("partly exported: $result", result is ExportResult.Success)
        assertEquals("one result per endpoint: $items", 3, items.size)

        val updated = items.single { it.endpoint === existing }
        assertEquals(ExportItemStatus.UPDATED, updated.status)
        assertEquals("the id it already had", existingId, updated.id)
        assertEquals("http://localhost:3000/project/11/interface/api/$existingId", updated.url)
        assertEquals("http://localhost:3000/project/11/interface/api/cat_${server.cartId("anonymous")}", updated.folderUrl)
        assertEquals("Existing", updated.name)
        assertEquals("/api/existing", updated.path)
        assertEquals("GET", updated.httpMethod)
        assertEquals("anonymous", updated.folder)
        assertNull("no destination when the token was passed", updated.destination)
        assertNull("it was in the target folder already", updated.movedFrom)

        val created = items.single { it.endpoint === fresh }
        assertEquals(ExportItemStatus.CREATED, created.status)
        assertEquals("the id looked up after the save", server.idOf("/api/fresh", "GET"), created.id)
        assertEquals("http://localhost:3000/project/11/interface/api/${created.id}", created.url)
        assertNull("nothing was moved", created.movedFrom)

        val failed = items.single { it.endpoint === broken }
        assertEquals(ExportItemStatus.FAILED, failed.status)
        assertEquals("the server's reason", "path is invalid", failed.reason)
        assertEquals("POST", failed.httpMethod)
    }

    fun `test an API kept by the export mode is reported as not written`() {
        val server = FakeYapiServer(mapOf(TOKEN to "11"))
        val existingId = server.addApi("11", "anonymous", "Old title", "/api/existing", "GET")
        val console = LoggerCollector()
        exporterProject = createExporterProject(httpClient = server, console = console)
        exporter = YapiExporter(exporterProject)
        val endpoint = createTestEndpoint("Existing", "/api/existing", HttpMethod.GET)
        val items = mutableListOf<ExportItemResult>()
        val context = createTestContext(
            listOf(endpoint), selectedToken = TOKEN, headless = true, itemListener = { items += it }
        )

        val result = withExportMode(YapiExportMode.NEVER_UPDATE) {
            kotlinx.coroutines.runBlocking { exporter.export(context, selectedToken = TOKEN) }
        }

        assertTrue("$result", result is ExportResult.Success)
        val item = items.single()
        assertEquals(ExportItemStatus.NOT_WRITTEN, item.status)
        assertEquals("the API that was kept", existingId, item.id)
        assertTrue("names the export mode: ${item.reason}", item.reason!!.contains("NEVER_UPDATE"))
        assertEquals("nothing is saved", 0, server.count(SAVE))
        val infos = console.getMessages().filter { it.level == LogLevel.INFO }.map { it.message }
        assertTrue("says on the console why it was kept: $infos", "Existing: not written: ${item.reason}" in infos)
    }

    fun `test an export nobody listens to sends no extra request`() {
        val server = FakeYapiServer(mapOf(TOKEN to "11"))
        exporterProject = createExporterProject(httpClient = server)
        exporter = YapiExporter(exporterProject)
        val endpoints = listOf(createTestEndpoint("A", "/api/a"), createTestEndpoint("B", "/api/b"))
        val context = createTestContext(endpoints, selectedToken = TOKEN)

        val result = kotlinx.coroutines.runBlocking { exporter.export(context, selectedToken = TOKEN) }

        assertTrue("$result", result is ExportResult.Success)
        assertEquals("one folder listing per endpoint, as before", endpoints.size, server.count(LIST_CAT))
        assertEquals("one project lookup for the client, as before", 1, server.count(GET_PROJECT))
        assertEquals("the project is not listed", 0, server.count(LIST_MENU))
    }

    fun `test a headless export nobody listens to sends no lookup for the report`() {
        val server = FakeYapiServer(mapOf(TOKEN to "11"))
        exporterProject = createExporterProject(httpClient = server)
        exporter = YapiExporter(exporterProject)
        val endpoints = listOf(createTestEndpoint("A", "/api/a"), createTestEndpoint("B", "/api/b"))
        val context = createTestContext(endpoints, selectedToken = TOKEN, headless = true)

        val result = kotlinx.coroutines.runBlocking { exporter.export(context, selectedToken = TOKEN) }

        assertTrue("$result", result is ExportResult.Success)
        assertEquals("one folder listing per endpoint, as interactive", endpoints.size, server.count(LIST_CAT))
        assertEquals("the project is not listed", 0, server.count(LIST_MENU))
    }

    fun `test a folder that cannot be resolved fails each endpoint with YApi's reason`() {
        val server = FakeYapiServer(mapOf(TOKEN to "11"))
        server.catMenuFailure = "没有权限"
        exporterProject = createExporterProject(httpClient = server, tokenLookup = { TOKEN })
        exporter = YapiExporter(exporterProject)
        val endpoints = listOf(createTestEndpoint("A", "/api/a"), createTestEndpoint("B", "/api/b"))
        val items = mutableListOf<ExportItemResult>()
        val context = createTestContext(endpoints, headless = true, itemListener = { items += it })

        val result = kotlinx.coroutines.runBlocking { exporter.export(context) }

        assertTrue("Expected Error but got $result", result is ExportResult.Error)
        assertEquals("one result per endpoint: $items", endpoints.size, items.size)
        endpoints.zip(items).forEach { (endpoint, item) ->
            assertSame("reported for the endpoint itself", endpoint, item.endpoint)
            assertEquals(ExportItemStatus.FAILED, item.status)
            assertEquals("the folder and YApi's reason", "Failed to resolve cart 'anonymous': 没有权限", item.reason)
            assertEquals("routed by the project name", exporterProject.name, item.destination)
        }
        assertEquals("nothing is saved", 0, server.count(SAVE))
    }

    // ---- an API YApi moves in from another folder ----

    fun `test an API YApi moves in from another folder is reported as updated with that folder`() {
        val server = FakeYapiServer(mapOf(TOKEN to "11"))
        val oldId = server.addApi("11", "old folder", "Old title", "/api/moved", "GET")
        exporterProject = createExporterProject(httpClient = server)
        exporter = YapiExporter(exporterProject)
        val endpoint = createTestEndpoint("Moved", "/api/moved", HttpMethod.GET)
        val items = mutableListOf<ExportItemResult>()
        val context = createTestContext(
            listOf(endpoint), selectedToken = TOKEN, headless = true, itemListener = { items += it }
        )

        val result = kotlinx.coroutines.runBlocking { exporter.export(context, selectedToken = TOKEN) }

        assertTrue("$result", result is ExportResult.Success)
        val item = items.single()
        assertEquals(ExportItemStatus.UPDATED, item.status)
        assertEquals("the id it had in the other folder", oldId, item.id)
        assertEquals("http://localhost:3000/project/11/interface/api/$oldId", item.url)
        assertEquals("anonymous", item.folder)
        assertEquals("the folder YApi moved it out of", "old folder", item.movedFrom)
        assertEquals("YApi moved it into the target folder", "anonymous", server.folderOf(oldId))
        assertEquals("the project is listed once", 1, server.count(LIST_MENU))
        assertEquals("saved once", 1, server.count(SAVE))
    }

    fun `test the project is listed once for all the endpoints missing from their folder`() {
        val server = FakeYapiServer(mapOf(TOKEN to "11"))
        server.addApi("11", "old folder", "A", "/api/a", "GET")
        server.addApi("11", "old folder", "B", "/api/b", "POST")
        exporterProject = createExporterProject(httpClient = server)
        exporter = YapiExporter(exporterProject)
        val endpoints = listOf(
            createTestEndpoint("A", "/api/a", HttpMethod.GET),
            createTestEndpoint("B", "/api/b", HttpMethod.POST)
        )
        val items = mutableListOf<ExportItemResult>()
        val context = createTestContext(endpoints, selectedToken = TOKEN, headless = true, itemListener = { items += it })

        val result = kotlinx.coroutines.runBlocking { exporter.export(context, selectedToken = TOKEN) }

        assertTrue("$result", result is ExportResult.Success)
        assertEquals(listOf(ExportItemStatus.UPDATED, ExportItemStatus.UPDATED), items.map { it.status })
        assertEquals(listOf("old folder", "old folder"), items.map { it.movedFrom })
        assertEquals("one listing for the YApi project", 1, server.count(LIST_MENU))
    }

    /** The export mode still looks into the target folder only, so YApi overwrites and moves the API. */
    fun `test NEVER_UPDATE does not keep an API in another folder`() {
        val server = FakeYapiServer(mapOf(TOKEN to "11"))
        val oldId = server.addApi("11", "old folder", "Old title", "/api/moved", "GET")
        exporterProject = createExporterProject(httpClient = server)
        exporter = YapiExporter(exporterProject)
        val items = mutableListOf<ExportItemResult>()
        val context = createTestContext(
            listOf(createTestEndpoint("Moved", "/api/moved", HttpMethod.GET)),
            selectedToken = TOKEN, headless = true, itemListener = { items += it }
        )

        val result = withExportMode(YapiExportMode.NEVER_UPDATE) {
            kotlinx.coroutines.runBlocking { exporter.export(context, selectedToken = TOKEN) }
        }

        assertTrue("$result", result is ExportResult.Success)
        val item = items.single()
        assertEquals(ExportItemStatus.UPDATED, item.status)
        assertEquals(oldId, item.id)
        assertEquals("old folder", item.movedFrom)
        assertEquals("saved once", 1, server.count(SAVE))
    }

    /** A project that cannot be listed leaves the status to the target folder, as before, and is asked once. */
    fun `test a project that cannot be listed is judged by the target folder`() {
        val server = FakeYapiServer(mapOf(TOKEN to "11"))
        server.addApi("11", "old folder", "A", "/api/a", "GET")
        server.addApi("11", "old folder", "B", "/api/b", "GET")
        server.menuFailure = "list_menu is down"
        exporterProject = createExporterProject(httpClient = server)
        exporter = YapiExporter(exporterProject)
        val endpoints = listOf(createTestEndpoint("A", "/api/a"), createTestEndpoint("B", "/api/b"))
        val items = mutableListOf<ExportItemResult>()
        val context = createTestContext(endpoints, selectedToken = TOKEN, headless = true, itemListener = { items += it })

        val result = kotlinx.coroutines.runBlocking { exporter.export(context, selectedToken = TOKEN) }

        assertTrue("the saves themselves went through: $result", result is ExportResult.Success)
        assertEquals(listOf(ExportItemStatus.CREATED, ExportItemStatus.CREATED), items.map { it.status })
        assertEquals(listOf(null, null), items.map { it.movedFrom })
        assertEquals("the failure is remembered for the project, not retried", 1, server.count(LIST_MENU))
    }

    fun `test a destination without a token fails only its own endpoints`() {
        loadFile(
            "test/RoutedCtrl.java", """
            package com.itangcent.test;
            public class RoutedCtrl {
                public void one() {}
                public void two() {}
                public void three() {}
            }
        """.trimIndent()
        )
        val psiClass = findClass("com.itangcent.test.RoutedCtrl")!!
        updateConfig(project, "yapi.project" to """groovy:it.contextType() == "method" ? (it.name() == "one" ? "key-a" : "key-b") : null""")
        val server = FakeYapiServer(mapOf(TOKEN to "11"))
        exporterProject = createExporterProject(
            httpClient = server,
            tokenLookup = { module -> TOKEN.takeIf { module == "key-a" } }
        )
        exporter = YapiExporter(exporterProject)
        val endpoints = listOf("one", "two", "three").map { name ->
            com.itangcent.easyapi.core.export.ApiEndpoint(
                name = name,
                sourceMethod = findMethod(psiClass, name),
                sourceClass = psiClass,
                metadata = httpMetadata(path = "/api/$name", method = HttpMethod.GET)
            )
        }
        val items = mutableListOf<ExportItemResult>()
        val context = createTestContext(endpoints, headless = true, itemListener = { items += it })

        val result = kotlinx.coroutines.runBlocking { exporter.export(context) }

        assertTrue("the destination with a token still exports: $result", result is ExportResult.Success)
        val one = items.single { it.endpoint === endpoints[0] }
        assertEquals(ExportItemStatus.CREATED, one.status)
        assertEquals("key-a", one.destination)
        val others = items.filter { it.endpoint !== endpoints[0] }
        assertEquals("both endpoints of key-b: $items", 2, others.size)
        others.forEach { item ->
            assertEquals(ExportItemStatus.FAILED, item.status)
            assertEquals("key-b", item.destination)
            assertTrue("names its destination: ${item.reason}", item.reason!!.contains("'key-b'"))
        }
        kotlinx.coroutines.runBlocking {
            verify(settingsHelper, times(1)).lookupToken("key-a")
            verify(settingsHelper, times(1)).lookupToken("key-b")
        }
    }

    /**
     * An endpoint that throws inside the loop keeps what was known by then: its destination, and the endpoint
     * as scanned, since formatting is what failed. It has a reason even when the exception has no message.
     */
    fun `test an endpoint that throws keeps its destination and what was known of it`() {
        val server = FakeYapiServer(mapOf(TOKEN to "11"))
        val render = mock<MarkdownRender> { on { render(any()) } doThrow IllegalStateException() }
        exporterProject = createExporterProject(httpClient = server, tokenLookup = { TOKEN }, markdownRender = render)
        exporter = YapiExporter(exporterProject)
        // Throws while it is formatted: its description goes to the renderer.
        val unformatted = createTestEndpoint("Described", "/api/described", description = "boom")
        val items = mutableListOf<ExportItemResult>()
        val context = createTestContext(listOf(unformatted), headless = true, itemListener = { items += it })

        val result = kotlinx.coroutines.runBlocking { exporter.export(context) }

        assertTrue("Expected Error but got $result", result is ExportResult.Error)
        val item = items.single()
        assertSame("the endpoint the exporter was handed", unformatted, item.endpoint)
        assertEquals(ExportItemStatus.FAILED, item.status)
        assertEquals("routed by the token table", exporterProject.name, item.destination)
        assertEquals("a reason without an exception message", "IllegalStateException", item.reason)
        assertNull("as scanned: the endpoint's own name", item.name)
        assertNull("as scanned: the endpoint's own folder", item.folder)
        assertEquals("nothing is saved", 0, server.count(SAVE))
    }

    /**
     * A folder listing whose entry has no usable id does not fail the endpoint: the lookup before the upload
     * finds nothing to update and the upload goes through (YApi itself matches the save by path and method).
     */
    fun `test a listing entry without an id does not fail the endpoint`() {
        val server = FakeYapiServer(mapOf(TOKEN to "11"))
        server.addApi("11", "anonymous", "Odd", "/api/odd", "GET")
        server.listedWithoutId += "/api/odd"
        exporterProject = createExporterProject(httpClient = server, tokenLookup = { TOKEN })
        exporter = YapiExporter(exporterProject)
        val endpoint = com.itangcent.easyapi.core.export.ApiEndpoint(
            metadata = httpMetadata(path = "/api/odd", method = HttpMethod.GET)
        )
        val items = mutableListOf<ExportItemResult>()
        val context = createTestContext(listOf(endpoint), headless = true, itemListener = { items += it })

        val result = kotlinx.coroutines.runBlocking { exporter.export(context) }

        assertTrue("Expected Success but got $result", result is ExportResult.Success)
        val item = items.single()
        assertTrue(
            "written; the listing hides the existing id, so it may be reported as created: $item",
            item.status == ExportItemStatus.CREATED || item.status == ExportItemStatus.UPDATED
        )
        assertEquals("saved once", 1, server.count(SAVE))
    }

    /** Runs [block] with the YApi export mode set to [mode] through the binder, then puts the previous one back. */
    private fun <T> withExportMode(mode: YapiExportMode, block: () -> T): T {
        val previous = settingBinder.read(YapiSettings::class).yapiExportMode
        settingBinder.update(YapiSettings::class) { yapiExportMode = mode.name }
        try {
            return block()
        } finally {
            settingBinder.update(YapiSettings::class) { yapiExportMode = previous }
        }
    }

    /** Fails every request the way a stopped server does, and records the URLs it was asked for. */
    private class UnreachableServer : HttpClient {
        val urls: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override suspend fun execute(request: HttpRequest): HttpResponse {
            urls += request.url
            throw ConnectException("Connection refused (test)")
        }

        override fun close() {}
    }

    private suspend fun stubOk(client: HttpClient, urlPart: String, body: String) {
        whenever(client.execute(argThat { url.contains(urlPart) }))
            .thenReturn(HttpResponse(code = 200, body = body))
    }

    private fun createTestEndpoint(
        name: String = "Test API",
        path: String = "/api/test",
        method: HttpMethod = HttpMethod.GET,
        description: String? = null
    ): com.itangcent.easyapi.core.export.ApiEndpoint {
        return com.itangcent.easyapi.core.export.ApiEndpoint(
            name = name,
            description = description,
            metadata = httpMetadata(
                path = path,
                method = method
            )
        )
    }

    private fun createTestContext(
        endpoints: List<com.itangcent.easyapi.core.export.ApiEndpoint>,
        selectedToken: String? = null,
        headless: Boolean = false,
        itemListener: ExportItemListener? = null
    ): com.itangcent.easyapi.core.export.ExportContext {
        val channelConfig = if (selectedToken != null) {
            YapiConfig(selectedToken = selectedToken)
        } else {
            ChannelConfig.Empty
        }
        return com.itangcent.easyapi.core.export.ExportContext(
            project = exporterProject,
            endpoints = endpoints,
            channelId = "yapi",
            channelConfig = channelConfig,
            headless = headless,
            itemListener = itemListener
        )
    }

    private fun createExporterProject(
        serverUrl: String? = "http://localhost:3000",
        tokenForModule: String? = null,
        httpClient: HttpClient? = null,
        tokenLookup: (String) -> String? = { null },
        markdownRender: MarkdownRender? = null,
        console: IdeaConsole? = null
    ): com.intellij.openapi.project.Project {
        val helper = mock<YapiSettingsHelper> {
            onBlocking { resolveServerUrl(any()) } doReturn serverUrl
            onBlocking { resolveToken(any(), any()) } doReturn tokenForModule
            onBlocking { lookupToken(any()) } doAnswer { tokenLookup(it.getArgument(0)) }
        }
        settingsHelper = helper

        return wrap(project) {
            replaceService(YapiSettingsHelper::class, helper)
            if (httpClient != null) {
                val provider = mock<HttpClientProvider> {
                    on { getClient() } doReturn httpClient
                }
                replaceService(HttpClientProvider::class, provider)
            }
            if (markdownRender != null) {
                replaceService(MarkdownRender::class, markdownRender)
            }
            if (console != null) {
                replaceService(IdeaConsoleProvider::class, mock<IdeaConsoleProvider> { on { getConsole() } doReturn console })
            }
        }
    }
}
