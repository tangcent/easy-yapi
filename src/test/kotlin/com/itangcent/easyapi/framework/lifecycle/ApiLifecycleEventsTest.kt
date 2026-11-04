package com.itangcent.easyapi.framework.lifecycle

import com.itangcent.easyapi.core.export.ClassExporter
import com.itangcent.easyapi.framework.feign.FeignClassExporter
import com.itangcent.easyapi.framework.grpc.GrpcClassExporter
import com.itangcent.easyapi.framework.jaxrs.JaxRsClassExporter
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.framework.springmvc.ActuatorEndpointExporter
import com.itangcent.easyapi.framework.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.core.psi.helper.DocHelper
import com.itangcent.easyapi.core.psi.helper.UnifiedDocHelper
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.itangcent.easyapi.core.logging.IdeaConsoleProvider
import com.itangcent.easyapi.core.logging.LoggerCollector
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class ApiLifecycleEventsTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var springExporter: SpringMvcClassExporter
    private lateinit var jaxRsExporter: JaxRsClassExporter
    private lateinit var feignExporter: FeignClassExporter
    private lateinit var grpcExporter: GrpcClassExporter
    private lateinit var actuatorExporter: ActuatorEndpointExporter

    /** Captures the rule scripts' `logger.info("evt:...")` markers. */
    private val console = LoggerCollector()

    override fun setUp() {
        super.setUp()
        loadCommonTestFiles()
        project.registerOrReplaceServiceInstance(
            IdeaConsoleProvider::class.java,
            mock<IdeaConsoleProvider> { on { getConsole() } doReturn console },
            testRootDisposable
        )
        springExporter = SpringMvcClassExporter(project)
        jaxRsExporter = JaxRsClassExporter(project)
        feignExporter = FeignClassExporter(project)
        grpcExporter = GrpcClassExporter(project)
        actuatorExporter = ActuatorEndpointExporter(project)
    }

    /** Event markers emitted by the rule scripts during this test. */
    private fun eventSequence(): List<String> =
        console.getMessages().map { it.message }.filter { it.startsWith("evt:") }

    private fun assertClassEnvelope(events: List<String>, className: String) {
        assertEquals(
            "First event must be class.before: $events",
            "evt:class.before:$className",
            events.firstOrNull()
        )
        assertEquals(
            "Last event must be class.after: $events",
            "evt:class.after:$className",
            events.lastOrNull()
        )
        assertEquals(
            "class.before must fire exactly once: $events",
            1,
            events.count { it == "evt:class.before:$className" }
        )
        assertEquals(
            "class.after must fire exactly once: $events",
            1,
            events.count { it == "evt:class.after:$className" }
        )
    }

    private fun assertNoMethodEvents(events: List<String>) {
        assertTrue(
            "Actuator/gRPC must not fire method-level events: $events",
            events.none { it.startsWith("evt:method.") || it.startsWith("evt:param.") }
        )
    }

    /**
     * Characterizes the full lifecycle sequence produced by one exporter run:
     * class.before → (method.before → param.before → param.after → export.after* → method.after)* → class.after.
     *
     * Counts and method order are the baseline captured from the pre-refactor exporters
     * (see T0.2 in `.spec/api-parse-aspect-refactor/tasks.md`); they must stay identical
     * through every migration step.
     */
    private fun assertLifecycle(
        events: List<String>,
        className: String,
        expectedMethods: List<String>,
        expectedParamPairs: Int,
        expectedExportAfter: Int,
        expectMethodEvents: Boolean = true
    ) {
        assertClassEnvelope(events, className)

        assertEquals(
            "method.before sequence: $events",
            expectedMethods.map { "evt:method.before:$it" },
            events.filter { it.startsWith("evt:method.before:") }
        )
        assertEquals(
            "method.after sequence: $events",
            expectedMethods.map { "evt:method.after:$it" },
            events.filter { it.startsWith("evt:method.after:") }
        )

        assertEquals(
            "param.before count: $events",
            expectedParamPairs,
            events.count { it.startsWith("evt:param.before:") }
        )
        assertEquals(
            "param.after count: $events",
            expectedParamPairs,
            events.count { it.startsWith("evt:param.after:") }
        )
        events.withIndex()
            .filter { it.value.startsWith("evt:param.before:") }
            .forEach { (index, event) ->
                assertEquals(
                    "param.before must be immediately followed by its after: $events",
                    "evt:param.after:" + event.removePrefix("evt:param.before:"),
                    events.getOrNull(index + 1)
                )
            }

        assertEquals(
            "export.after count: $events",
            expectedExportAfter,
            events.count { it == "evt:export.after" }
        )

        if (!expectMethodEvents) {
            assertNoMethodEvents(events)
            return
        }

        // Each method block must be well formed and must not enclose another method block.
        val starts = events.withIndex().filter { it.value.startsWith("evt:method.before:") }
        starts.forEachIndexed { i, (startIndex, startEvent) ->
            val name = startEvent.removePrefix("evt:method.before:")
            val endIndex = events.indexOf("evt:method.after:$name")
            assertTrue("method $name: after must come after before: $events", endIndex > startIndex)
            assertTrue(
                "method $name: block must close before the next method starts: $events",
                endIndex < (starts.getOrNull(i + 1)?.index ?: Int.MAX_VALUE)
            )
        }

        // export.after always fires inside a method block, after before and before after.
        val firstBefore = events.indexOfFirst { it.startsWith("evt:method.before:") }
        val lastAfter = events.indexOfLast { it.startsWith("evt:method.after:") }
        events.withIndex()
            .filter { it.value == "evt:export.after" }
            .forEach { (index, _) ->
                assertTrue(
                    "export.after must fire inside a method block: $events",
                    index > firstBefore && index < lastAfter
                )
            }
    }

    private fun loadCommonTestFiles() {
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/PutMapping.java")
        loadFile("spring/DeleteMapping.java")
        loadFile("spring/PatchMapping.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/RequestHeader.java")
        loadFile("spring/ModelAttribute.java")
        loadFile("spring/RestController.java")
        loadFile("spring/Controller.java")
        loadFile("model/Result.java")
        loadFile("model/IResult.java")
        loadFile("model/UserInfo.java")
        loadFile("api/BaseController.java")
        loadFile("api/UserCtrl.java")

        loadFile("jaxrs/GET.java")
        loadFile("jaxrs/POST.java")
        loadFile("jaxrs/PUT.java")
        loadFile("jaxrs/DELETE.java")
        loadFile("jaxrs/PATCH.java")
        loadFile("jaxrs/OPTIONS.java")
        loadFile("jaxrs/HEAD.java")
        loadFile("jaxrs/Path.java")
        loadFile("jaxrs/PathParam.java")
        loadFile("jaxrs/QueryParam.java")
        loadFile("jaxrs/FormParam.java")
        loadFile("jaxrs/HeaderParam.java")
        loadFile("jaxrs/CookieParam.java")
        loadFile("jaxrs/BeanParam.java")
        loadFile("jaxrs/DefaultValue.java")
        loadFile("jaxrs/Consumes.java")
        loadFile("jaxrs/Produces.java")
        loadFile("jaxrs/HttpMethod.java")
        loadFile("api/jaxrs/UserDTO.java")
        loadFile("api/jaxrs/UserResource.java")

        loadFile("feign/RequestLine.java")
        loadFile("feign/Headers.java")
        loadFile("feign/Body.java")
        loadFile("feign/Param.java")
        loadFile("spring/FeignClient.java")
        loadFile("api/feign/UserClient.java")

        loadFile("grpc/BindableService.java")
        loadFile("grpc/GrpcService.java")
        loadFile("grpc/StreamObserver.java")
        loadFile("grpc/EchoRequest.java")
        loadFile("grpc/EchoResponse.java")
        loadFile("grpc/EchoServiceGrpc.java")
        loadFile("grpc/EchoServiceImpl.java")
        loadFile("grpc/UserInfo.java")

        loadFile("spring/Endpoint.java")
        loadFile("spring/ReadOperation.java")
        loadFile("spring/WriteOperation.java")
        loadFile("spring/DeleteOperation.java")
        loadFile("spring/Selector.java")
        loadFile("spring/WebEndpoint.java")
        loadFile("spring/ControllerEndpoint.java")
        loadFile("spring/RestControllerEndpoint.java")
        loadFile("api/actuator/StandardEndpoint.java")
    }

    override fun createConfigReader() = TestConfigReader.fromConfigText(
        project,
        """
        api.class.parse.before=groovy:logger.info("evt:class.before:" + it.name())
        api.class.parse.after=groovy:logger.info("evt:class.after:" + it.name())
        api.method.parse.before=groovy:logger.info("evt:method.before:" + it.name())
        api.method.parse.after=groovy:logger.info("evt:method.after:" + it.name())
        api.param.parse.before=groovy:logger.info("evt:param.before:" + it.name())
        api.param.parse.after=groovy:logger.info("evt:param.after:" + it.name())
        export.after=groovy:logger.info("evt:export.after"); api.appendDesc("\n```\n" + api.toCurl() + "\n```\n")
        """.trimIndent()
    )

    // ── Spring MVC lifecycle events ──────────────────────────────

    fun testSpringMvcClassParseEvents() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = springExporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())

        assertLifecycle(
            events = eventSequence(),
            className = "UserCtrl",
            expectedMethods = listOf(
                "greeting", "get", "create", "update", "getByCookie", "getFromSession",
                "updateWithBody", "simpleRequest", "explicitGetWithBody", "updateWithModel", "ctrlName"
            ),
            expectedParamPairs = 7,
            expectedExportAfter = 12
        )
        assertEquals("One export.after event per endpoint", 12, endpoints.size)
    }

    fun testSpringMvcExportAfterEvent() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = springExporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())
        for (endpoint in endpoints) {
            assertNotNull("Endpoint should have source method", endpoint.sourceMethod)
        }
    }

    // ── JAX-RS lifecycle events ──────────────────────────────────

    fun testJaxRsClassParseEvents() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = jaxRsExporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())

        assertLifecycle(
            events = eventSequence(),
            className = "UserResource",
            expectedMethods = listOf(
                "greeting", "set", "get", "getById", "add",
                "list", "listTypeInPath", "delete", "types", "setName"
            ),
            expectedParamPairs = 0,
            expectedExportAfter = 10
        )
        assertEquals("One export.after event per endpoint", 10, endpoints.size)
    }

    fun testJaxRsExportAfterEvent() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = jaxRsExporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())
        for (endpoint in endpoints) {
            assertNotNull("Endpoint should have source method", endpoint.sourceMethod)
        }
    }

    // ── Feign lifecycle events ───────────────────────────────────

    fun testFeignClassParseEvents() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.client.UserClient")
        assertNotNull(psiClass)

        val endpoints = feignExporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())

        assertLifecycle(
            events = eventSequence(),
            className = "UserClient",
            expectedMethods = listOf("greeting", "get", "add", "update"),
            expectedParamPairs = 0,
            expectedExportAfter = 4
        )
        assertEquals("One export.after event per endpoint", 4, endpoints.size)
    }

    fun testFeignExportAfterEvent() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.client.UserClient")
        assertNotNull(psiClass)

        val endpoints = feignExporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())
        for (endpoint in endpoints) {
            assertNotNull("Endpoint should have source method", endpoint.sourceMethod)
        }
    }

    // ── gRPC lifecycle events ────────────────────────────────────

    fun testGrpcClassParseEvents() = runTest {
        val psiClass = findClass("com.itangcent.grpc.service.EchoServiceImpl")
        assertNotNull(psiClass)

        val endpoints = grpcExporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())

        assertLifecycle(
            events = eventSequence(),
            className = "EchoServiceImpl",
            expectedMethods = emptyList(),
            expectedParamPairs = 0,
            expectedExportAfter = 2,
            expectMethodEvents = false
        )
        assertEquals("One export.after event per endpoint", 2, endpoints.size)
    }

    fun testGrpcExportAfterEvent() = runTest {
        val psiClass = findClass("com.itangcent.grpc.service.EchoServiceImpl")
        assertNotNull(psiClass)

        val endpoints = grpcExporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())
        for (endpoint in endpoints) {
            assertNotNull("Endpoint should have source method", endpoint.sourceMethod)
        }
    }

    // ── Actuator lifecycle events ────────────────────────────────

    fun testActuatorClassParseEvents() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.controller.StandardEndpoint")
        assertNotNull(psiClass)

        val endpoints = actuatorExporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())

        assertLifecycle(
            events = eventSequence(),
            className = "StandardEndpoint",
            expectedMethods = emptyList(),
            expectedParamPairs = 0,
            expectedExportAfter = 3,
            expectMethodEvents = false
        )
        assertEquals("One export.after event per endpoint", 3, endpoints.size)
    }

    fun testActuatorExportAfterEvent() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.controller.StandardEndpoint")
        assertNotNull(psiClass)

        val endpoints = actuatorExporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())
        for (endpoint in endpoints) {
            assertNotNull("Endpoint should have source method", endpoint.sourceMethod)
        }
    }

    // ── Non-matching classes should not fire events ──────────────

    fun testSpringMvcNonControllerNoEvents() = runTest {
        val psiClass = findClass("com.itangcent.model.UserInfo")
        assertNotNull(psiClass)

        val endpoints = springExporter.export(psiClass!!)
        assertTrue("Should not export endpoints for non-controller", endpoints.isEmpty())
        assertTrue("Non-controller must not fire lifecycle events", eventSequence().isEmpty())
    }

    // ── export.after rule: api.toCurl() ──────────────────

    /**
     * Verifies the `export.after` rule's `api.toCurl()` (default host) appends a
     * cURL command containing `curl`, the HTTP method, and the `{{host}}` default
     * placeholder to the endpoint description.
     */
    fun testSpringMvcExportAfterToCurlDefaultHost() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = springExporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())

        val ep = endpoints.first()
        val desc = ep.description ?: ""
        assertTrue("description should contain curl command: $desc", desc.contains("curl"))
        assertTrue("description should contain {{host}} default: $desc", desc.contains("{{host}}"))
        // The HTTP method should appear in the curl command (-X GET / --request GET)
        val method = (ep.metadata as? com.itangcent.easyapi.core.export.HttpMetadata)?.method?.name
        assertNotNull("endpoint should have HTTP method", method)
        assertTrue("description should contain method $method: $desc", desc.contains(method!!))
    }

    /**
     * Verifies `api.toCurl("https://api.example.com")` bakes the explicit host
     * into the generated cURL command (host parameter).
     *
     * Uses a direct [com.itangcent.easyapi.core.rule.context.ScriptApiEndpoint] call
     * (rather than a second rule config) because [createConfigReader] is shared
     * across the whole class. The direct call exercises the same code path:
     * `CurlBuilder.buildSync(project, endpoint, host, options)` with
     * `runPreScripts=false`.
     */
    fun testToCurlWithExplicitHost() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = springExporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())

        val ep = endpoints.first()
        val scriptEp = com.itangcent.easyapi.core.rule.context.ScriptApiEndpoint(ep, project)
        val curl = scriptEp.toCurl("https://api.example.com")
        assertTrue("curl should contain explicit host: $curl", curl.contains("https://api.example.com"))
        assertTrue("curl should not contain {{host}} placeholder when explicit host given: $curl",
            !curl.contains("{{host}}"))
    }
}
