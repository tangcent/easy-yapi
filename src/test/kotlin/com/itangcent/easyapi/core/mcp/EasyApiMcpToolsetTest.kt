package com.itangcent.easyapi.core.mcp

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.intellij.testFramework.replaceService
import com.itangcent.easyapi.core.export.ExternalChangeSync
import com.itangcent.easyapi.core.export.FakeExternalChangeSync
import com.itangcent.easyapi.core.export.SyncOutcome
import com.itangcent.easyapi.core.internal.threading.IdeDispatchers
import com.itangcent.easyapi.core.logging.IdeaConsole
import com.itangcent.easyapi.core.logging.IdeaConsoleProvider
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
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

    fun testExposesExactlyOneToolUnderItsPublicName() {
        val tools = mcpTools()

        assertEquals("exactly one tool: ${tools.map { it.name }}", 1, tools.size)
        assertEquals(
            "public tool name",
            "easyapi_scan_endpoints",
            tools.single().findAnnotation<McpTool>()!!.name
        )
        assertTrue(
            "the tool is described",
            tools.single().findAnnotation<McpDescription>()?.description?.isNotBlank() == true
        )
    }

    /** `projectPath` must keep that exact name: newer IDEs add an implicit parameter of the same name and merge the two. */
    fun testToolParametersAreOptionalNullableAndDescribed() {
        val parameters = mcpTools().single().valueParameters

        assertEquals(
            "parameter names",
            listOf("classes", "methods", "files", "projectPath"),
            parameters.map { it.name }
        )
        parameters.forEach { parameter ->
            assertTrue("${parameter.name} is optional", parameter.isOptional)
            assertTrue("${parameter.name} is nullable", parameter.type.isMarkedNullable)
            assertTrue(
                "${parameter.name} is described",
                parameter.findAnnotation<McpDescription>()?.description?.isNotBlank() == true
            )
        }
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
        val console = mock<IdeaConsole>()
        val provider = mock<IdeaConsoleProvider>()
        whenever(provider.getConsole()).thenReturn(console)
        project.registerOrReplaceServiceInstance(IdeaConsoleProvider::class.java, provider, testRootDisposable)

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

    private fun parse(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    /** Makes [fake] the project's sync until the test ends. */
    private fun useSync(fake: FakeExternalChangeSync) =
        project.replaceService(ExternalChangeSync::class.java, fake, testRootDisposable)

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
    }
}
