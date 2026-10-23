package com.itangcent.easyapi.core.mcp

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.testFramework.replaceService
import com.itangcent.easyapi.channel.spi.Channel
import com.itangcent.easyapi.core.export.ExportItemResult
import com.itangcent.easyapi.core.export.ExportItemStatus
import com.itangcent.easyapi.core.export.ExportResult
import com.itangcent.easyapi.core.export.ExternalChangeSync
import com.itangcent.easyapi.core.export.FakeExternalChangeSync
import com.itangcent.easyapi.core.export.ProbeChannel
import com.itangcent.easyapi.core.export.SyncOutcome
import com.itangcent.easyapi.core.internal.PluginInfo.PLUGIN_ID
import com.itangcent.easyapi.core.internal.threading.IdeDispatchers
import com.itangcent.easyapi.core.logging.IdeaConsole
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import com.itangcent.easyapi.testFramework.recordConsole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mockingDetails
import org.mockito.kotlin.verify
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.valueParameters

/**
 * Contract tests for [EasyApiMcpToolset]: the tool name and parameters an MCP client sees, the
 * optional descriptor wiring, the results it returns, and the exception mapping of
 * [EasyApiMcpToolset.runTool].
 *
 * Names are asserted as literals on purpose: renaming the tool or a parameter breaks every client
 * that calls it, so it must also break this test.
 *
 * The sync of files changed outside the IDE is a [FakeExternalChangeSync]: the platform's waits for
 * the EDT, which this test holds while it waits for the tool.
 */
class EasyApiMcpToolsetTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun createConfigReader() = TestConfigReader.empty(project)

    override fun setUp() {
        super.setUp()
        useSync(FakeExternalChangeSync())
    }

    // ---- the tool as clients see it ----

    fun testExposesExactlyTwoToolsUnderTheirPublicNames() {
        val tools = mcpTools()

        assertEquals(
            "public tool names",
            listOf("easyapi_export", "easyapi_scan_endpoints"),
            tools.map { it.findAnnotation<McpTool>()!!.name }.sorted()
        )
        tools.forEach { tool ->
            assertTrue(
                "${tool.name} is described",
                tool.findAnnotation<McpDescription>()?.description?.isNotBlank() == true
            )
        }
    }

    /** `projectPath` must keep that exact name: newer IDEs add an implicit parameter of the same name and merge the two. */
    fun testToolParametersAreOptionalNullableAndDescribed() {
        assertEquals(
            "scan tool parameter names",
            listOf("classes", "methods", "files", "projectPath"),
            mcpTool("easyapi_scan_endpoints").valueParameters.map { it.name }
        )
        assertEquals(
            "export tool parameter names",
            listOf("classes", "methods", "files", "projectPath", "channel", "options"),
            mcpTool("easyapi_export").valueParameters.map { it.name }
        )
        mcpTools().flatMap { it.valueParameters }.forEach { parameter ->
            assertTrue("${parameter.name} is optional", parameter.isOptional)
            assertTrue("${parameter.name} is nullable", parameter.type.isMarkedNullable)
            assertTrue(
                "${parameter.name} is described",
                parameter.findAnnotation<McpDescription>()?.description?.isNotBlank() == true
            )
        }
    }

    /** The 2025.2.1 MCP server turns a map of strings into an object whose values are strings. */
    fun testExportOptionsAreNameValuePairs() {
        val options = mcpTool("easyapi_export").valueParameters.single { it.name == "options" }.type

        assertEquals("a map", Map::class, options.classifier)
        assertEquals(
            "string names and string values",
            listOf(String::class, String::class),
            options.arguments.map { it.type?.classifier }
        )
    }

    // ---- results ----

    fun testSuccessResultNamesTheProject() {
        addPingController()

        val result = parse(call(classes = listOf(PING_CTRL), projectPath = project.basePath))

        assertFalse("not an error: $result", result.has("error"))
        val resolvedProject = result.getAsJsonObject("project")
        assertEquals("project name", project.name, resolvedProject.get("name").asString)
        assertEquals("project root", project.basePath, resolvedProject.get("basePath").asString)
        assertEquals("the controller's endpoint", 1, result.get("count").asInt)
        assertEquals("nothing skipped, and the field is still there", 0, result.getAsJsonArray("skipped").size())
        assertFalse("no source field: every call scans: $result", result.has("source"))
    }

    fun testNoTargetComesBackAsTargetsRequired() {
        val error = parse(call(projectPath = project.basePath)).getAsJsonObject("error")

        assertNotNull("an error result", error)
        assertEquals("code", "TARGETS_REQUIRED", error.get("code").asString)
        assertEquals("names the project", project.name, error.getAsJsonObject("project").get("name").asString)
    }

    fun testTargetWithoutEndpointsComesBackAsNoEndpoints() {
        addPlainClass()

        val error = parse(call(classes = listOf(PLAIN), projectPath = project.basePath)).getAsJsonObject("error")

        assertNotNull("an error result", error)
        assertEquals("code", "NO_ENDPOINTS", error.get("code").asString)
        assertEquals("names the project", project.name, error.getAsJsonObject("project").get("name").asString)
        assertEquals(
            "lists the skipped target",
            listOf("class" to PLAIN),
            error.getAsJsonArray("skipped").map { it.asJsonObject.get("kind").asString to it.asJsonObject.get("input").asString }
        )
    }

    fun testSkippedTargetsAreLoggedWithTheirReasons() {
        addPingController()
        addPlainClass()
        val console = recordConsole(project, testRootDisposable)

        val result = parse(call(classes = listOf(PING_CTRL, PLAIN), projectPath = project.basePath))

        assertEquals("the plain class is skipped: $result", 1, result.getAsJsonArray("skipped").size())
        val logged = argumentCaptor<String>()
        verify(console, atLeastOnce()).info(logged.capture())
        val line = logged.allValues.singleOrNull { it.startsWith("MCP easyapi_scan_endpoints returned ") }
        assertNotNull("one line for the result: ${logged.allValues}", line)
        assertTrue(
            "names the skipped target and why: $line",
            line!!.contains("\n- class '$PLAIN': no endpoint in this class: ")
        )
    }

    fun testUnresolvedTargetsComeBackAsAnErrorResult() {
        val error = parse(call(classes = listOf("NoSuchClass"), projectPath = project.basePath)).getAsJsonObject("error")

        assertNotNull("an error result", error)
        assertEquals("code", "TARGETS_UNRESOLVED", error.get("code").asString)
        assertEquals("names the project", project.name, error.getAsJsonObject("project").get("name").asString)
        assertEquals(
            "lists the bad target",
            listOf("NoSuchClass"),
            error.getAsJsonArray("failures").map { it.asJsonObject.get("input").asString }
        )
    }

    fun testUnsavedChangesComeBackAsUnsavedInIde() {
        addPingController()
        val files = listOf("src/main/java/com/example/mcp/PingCtrl.java")
        useSync(FakeExternalChangeSync(SyncOutcome.UnsavedInIde(files)))

        val error = parse(call(classes = listOf(PING_CTRL), projectPath = project.basePath)).getAsJsonObject("error")

        assertNotNull("an error result", error)
        assertEquals("code", "UNSAVED_IN_IDE", error.get("code").asString)
        assertEquals("names the project", project.name, error.getAsJsonObject("project").get("name").asString)
        assertEquals("lists the files", files, error.getAsJsonArray("files").map { it.asString })
    }

    fun testUnknownProjectComesBackAsAnErrorResult() {
        val outside = File(project.basePath!!).parentFile.resolve("not-an-open-project").path

        val error = parse(call(classes = listOf(PLAIN), projectPath = outside)).getAsJsonObject("error")

        assertNotNull("an error result", error)
        assertEquals("code", "PROJECT_NOT_FOUND", error.get("code").asString)
        assertTrue(
            "lists the open projects: $error",
            error.getAsJsonArray("openProjects").any { it.asString == project.basePath }
        )
    }

    // ---- the export tool ----

    /** Like the scan tool, it needs a target; the channel it would have used is yapi, the default. */
    fun testExportWithoutTargetsComesBackAsTargetsRequired() {
        val console = recordConsole(project, testRootDisposable)

        val error = parse(export(projectPath = project.basePath)).getAsJsonObject("error")

        assertNotNull("an error result", error)
        assertEquals("code", "TARGETS_REQUIRED", error.get("code").asString)
        assertEquals("names the project", project.name, error.getAsJsonObject("project").get("name").asString)
        val called = linesOf(console).singleOrNull { it.startsWith("MCP easyapi_export called: ") }
        assertNotNull("one line for the call: ${linesOf(console)}", called)
        assertTrue("the default channel: $called", called!!.contains("channel=yapi"))
    }

    /** A blank channel counts as none, as a blank projectPath does. */
    fun testBlankChannelIsTheDefault() {
        val console = recordConsole(project, testRootDisposable)

        val error = parse(export(channel = "  ", projectPath = project.basePath)).getAsJsonObject("error")

        assertEquals("code", "TARGETS_REQUIRED", error.get("code").asString)
        val called = linesOf(console).singleOrNull { it.startsWith("MCP easyapi_export called: ") }
        assertNotNull("one line for the call: ${linesOf(console)}", called)
        assertTrue("the default channel: $called", called!!.contains("channel=yapi"))
    }

    /**
     * Nothing written is the export failing, logged as a warning, so a console set to warnings still shows it;
     * a wrong argument stays at info.
     */
    fun testExportThatWritesNothingIsLoggedAsAWarning() {
        addPingController()
        project.extensionArea
            .getExtensionPoint<Channel>("$PLUGIN_ID.channel")
            .registerExtension(tokenProbe(error = "the destination is down"), testRootDisposable)
        val console = recordConsole(project, testRootDisposable)

        val failed = parse(export(classes = listOf(PING_CTRL), channel = PROBE_CHANNEL, projectPath = project.basePath))
        val unknown = parse(export(classes = listOf(PING_CTRL), channel = "nope", projectPath = project.basePath))

        assertEquals("code", "EXPORT_FAILED", failed.getAsJsonObject("error").get("code").asString)
        assertEquals("code", "CHANNEL_NOT_FOUND", unknown.getAsJsonObject("error").get("code").asString)
        val warned = argumentCaptor<String>()
        verify(console, atLeastOnce()).warn(warned.capture(), anyOrNull())
        assertEquals(
            "one warning, for the export that wrote nothing",
            listOf("MCP easyapi_export failed: Nothing was exported: the destination is down"),
            warned.allValues.filter { it.startsWith("MCP easyapi_export ") }
        )
        val logged = argumentCaptor<String>()
        verify(console, atLeastOnce()).info(logged.capture())
        assertTrue(
            "the unknown channel at info: ${logged.allValues}",
            logged.allValues.any { it.startsWith("MCP easyapi_export failed: Unknown channel 'nope'.") }
        )
    }

    fun testUnknownChannelComesBackWithTheChannelsThatCanExport() {
        addPingController()

        val error = parse(export(classes = listOf(PING_CTRL), channel = "nope", projectPath = project.basePath))
            .getAsJsonObject("error")

        assertNotNull("an error result", error)
        assertEquals("code", "CHANNEL_NOT_FOUND", error.get("code").asString)
        assertEquals("names the project", project.name, error.getAsJsonObject("project").get("name").asString)
        assertTrue(
            "lists the channels that can export: $error",
            error.getAsJsonArray("channels").any { it.asString == "yapi" }
        )
    }

    /**
     * An option value may be a secret, such as a token: the log names the option but never shows its value,
     * and nor does the result.
     */
    fun testExportLogsOptionNamesButNeverTheirValues() {
        addPingController()
        project.extensionArea
            .getExtensionPoint<Channel>("$PLUGIN_ID.channel")
            .registerExtension(tokenProbe(), testRootDisposable)
        val console = recordConsole(project, testRootDisposable)

        val text = export(
            classes = listOf(PING_CTRL), channel = PROBE_CHANNEL, options = mapOf("token" to SECRET),
            projectPath = project.basePath
        )

        val result = parse(text)
        assertFalse("not an error: $result", result.has("error"))
        assertEquals("project name", project.name, result.getAsJsonObject("project").get("name").asString)
        assertEquals("channel", PROBE_CHANNEL, result.get("channel").asString)
        assertEquals("the controller's endpoint, created", 1, result.get("created").asInt)
        assertFalse("the result never holds the token: $text", text.contains(SECRET))
        val lines = linesOf(console)
        val called = lines.singleOrNull { it.startsWith("MCP easyapi_export called: ") }
        assertNotNull("one line for the call: $lines", called)
        assertTrue("names the option: $called", called!!.contains("options=[token]"))
        assertTrue("no line holds the token: $lines", lines.none { it.contains(SECRET) })
    }

    // ---- wiring ----

    fun testOptionalDescriptorRegistersTheToolset() {
        val extensions = parseFromClasspath("META-INF/easy-api-mcp.xml").getElementsByTagName("extensions")

        assertEquals("one extensions block", 1, extensions.length)
        val block = extensions.item(0) as Element
        assertEquals("namespace", "com.intellij.mcpServer", block.getAttribute("defaultExtensionNs"))
        val toolsets = block.getElementsByTagName("mcpToolset")
        assertEquals("one toolset", 1, toolsets.length)
        assertEquals(
            "implementation",
            EasyApiMcpToolset::class.java.name,
            (toolsets.item(0) as Element).getAttribute("implementation")
        )
    }

    fun testPluginXmlDeclaresTheOptionalMcpDependency() {
        val depends = parseFromClasspath("META-INF/plugin.xml").getElementsByTagName("depends")
        val mcp = (0 until depends.length)
            .map { depends.item(it) as Element }
            .filter { it.textContent.trim() == "com.intellij.mcpServer" }

        assertEquals("one dependency on the MCP Server plugin", 1, mcp.size)
        assertEquals("optional", "true", mcp.single().getAttribute("optional"))
        assertEquals("descriptor", "easy-api-mcp.xml", mcp.single().getAttribute("config-file"))
    }

    /** Needs the MCP Server plugin loaded in the test IDE; without it the extension point is missing. */
    fun testToolsetIsRegisteredWhenTheMcpServerPluginIsLoaded() {
        assertTrue(
            "the toolset should be discoverable through the MCP server's extension point",
            McpToolset.EP.extensionList.any { it is EasyApiMcpToolset }
        )
    }

    // ---- runTool ----

    fun testRunToolReturnsTheBodyResult() {
        val result = runBlocking {
            EasyApiMcpToolset().runTool(PROBE, project.basePath) { resolved, summary -> "${resolved.name} ${summary.name}" }
        }

        assertEquals("the body's result, for the project the path names", "${project.name} ${project.name}", result)
    }

    fun testUnexpectedExceptionBecomesAnInternalErrorResult() {
        val result = runBlocking {
            EasyApiMcpToolset().runTool(PROBE, project.basePath) { _, _ -> throw IllegalStateException("boom") }
        }

        val error = parse(result).getAsJsonObject("error")
        assertNotNull("an error result, not an exception: $result", error)
        assertEquals("code", "INTERNAL_ERROR", error.get("code").asString)
        val message = error.get("message").asString
        assertTrue("names the tool: $message", message.contains(PROBE))
        assertTrue("keeps the cause: $message", message.contains("boom"))
    }

    fun testCancellationIsRethrown() {
        val error = runBlocking {
            try {
                EasyApiMcpToolset().runTool(PROBE, project.basePath) { _, _ -> throw CancellationException("stop") }
                null
            } catch (e: CancellationException) {
                e
            }
        }

        assertNotNull("cancellation is not turned into an error result", error)
        assertEquals("the same cancellation", "stop", error!!.message)
    }

    /** The platform's cancellation is rethrown as is too, not turned into an error result. */
    fun testProcessCanceledExceptionIsRethrown() {
        val thrown = runBlocking {
            try {
                EasyApiMcpToolset().runTool(PROBE, project.basePath) { _, _ -> throw ProcessCanceledException() }
                null
            } catch (e: ProcessCanceledException) {
                e
            }
        }

        assertNotNull("rethrown, not an error result", thrown)
    }

    /** The MCP server may call on any thread, the EDT included (as this test does): the body runs on the background pool. */
    fun testRunToolRunsTheBodyOnTheBackgroundPool() {
        var onEdt: Boolean? = null

        val thread = runBlocking {
            EasyApiMcpToolset().runTool(PROBE, project.basePath) { _, _ ->
                onEdt = IdeDispatchers.isDispatchThread
                Thread.currentThread().name
            }
        }

        assertTrue("the background pool: $thread", thread.startsWith("EasyAPI-background"))
        assertEquals("not the EDT", false, onEdt)
    }

    // ---- helpers ----

    private fun call(classes: List<String>? = null, projectPath: String? = null): String =
        runBlocking { EasyApiMcpToolset().scanEndpoints(classes = classes, projectPath = projectPath) }

    private fun export(
        classes: List<String>? = null,
        channel: String? = null,
        options: Map<String, String>? = null,
        projectPath: String? = null
    ): String = runBlocking {
        EasyApiMcpToolset().exportEndpoints(classes = classes, projectPath = projectPath, channel = channel, options = options)
    }

    private fun parse(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    /** Makes [fake] the project's sync until the test ends. */
    private fun useSync(fake: FakeExternalChangeSync) =
        project.replaceService(ExternalChangeSync::class.java, fake, testRootDisposable)

    /** Every text [console] received, at any level. */
    private fun linesOf(console: IdeaConsole): List<String> =
        mockingDetails(console).invocations.flatMap { invocation -> invocation.arguments.filterIsInstance<String>() }

    /** A [ProbeChannel] that reports every endpoint as created; or, given an [error], fails as a whole with it. */
    private fun tokenProbe(error: String? = null) = ProbeChannel(PROBE_CHANNEL) { context ->
        if (error != null) {
            ExportResult.Error(error)
        } else {
            context.endpointsToExport.forEach { context.itemListener?.onItem(ExportItemResult(it, ExportItemStatus.CREATED)) }
            ExportResult.Success(context.endpointsToExport.size, "probe")
        }
    }

    private fun addPlainClass() {
        loadFile("mcp/Plain.java", "package com.example.mcp; public class Plain {}")
        runBlocking { waitForClass(PLAIN) }
    }

    private fun addPingController() {
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/RestController.java")
        loadFile(
            "mcp/PingCtrl.java",
            """
            package com.example.mcp;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class PingCtrl {

                @GetMapping("/ping")
                public String ping() {
                    return null;
                }
            }
            """.trimIndent()
        )
        runBlocking { waitForClass(PING_CTRL) }
    }

    private fun mcpTools(): List<KFunction<*>> =
        EasyApiMcpToolset::class.declaredMemberFunctions.filter { it.findAnnotation<McpTool>() != null }

    private fun mcpTool(name: String): KFunction<*> = mcpTools().single { it.findAnnotation<McpTool>()!!.name == name }

    /** Mirrors `ToolWindowLookupGuardTest.loadFromClasspath`. */
    private fun parseFromClasspath(resourcePath: String): Document {
        val stream = javaClass.getResourceAsStream("/$resourcePath")
            ?: throw AssertionError(
                "/$resourcePath not found on the test classpath — expected it at src/main/resources/$resourcePath"
            )
        return stream.use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
    }

    private companion object {
        const val PROBE = "probe_tool"
        const val PLAIN = "com.example.mcp.Plain"
        const val PING_CTRL = "com.example.mcp.PingCtrl"
        const val PROBE_CHANNEL = "mcp-token-probe"
        const val SECRET = "secret-123"
    }
}
