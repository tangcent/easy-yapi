package com.itangcent.easyapi.framework.lifecycle

import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.itangcent.easyapi.core.logging.IdeaConsoleProvider
import com.itangcent.easyapi.core.logging.LoggerCollector
import com.itangcent.easyapi.framework.feign.FeignClassExporter
import com.itangcent.easyapi.framework.grpc.GrpcClassExporter
import com.itangcent.easyapi.framework.springmvc.ActuatorEndpointExporter
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Acceptance test for R4: lifecycle event rules receive a `ResolvedMethod` whose
 * generic type arguments are substituted against the exported class.
 *
 * The non-Spring exporters (Feign, Actuator, gRPC) used to fire their lifecycle
 * events with the raw `PsiMethod`. After the aspect refactor they pass a
 * `ResolvedMethod` built with the exported class as owner.
 *
 * Only Feign can actually observe the substitution end-to-end: it discovers
 * methods through `ClassType.suitableMethods()`, which walks the whole hierarchy,
 * so a method inherited from a generic base is exported with the concrete type
 * argument. `ActuatorEndpointScanner` and `GrpcMethodResolver` iterate
 * `PsiClass#getMethods` (declared members only), so an inherited generic
 * operation never reaches those exporters — their fixtures therefore keep the
 * generic base (documenting the boundary) and bind `T` on a declared operation.
 *
 * Rule scripts log `it.returnType().name()` plus the argument type names
 * (`it.argTypes()*.name()` / `it.args()*.type()*.name()`) so the types seen by
 * the event are visible in the console stream.
 */
class ResolvedMethodLifecycleTest : EasyApiLightCodeInsightFixtureTestCase() {

    /** Captures the rule scripts' `logger.info("evt:...")` markers. */
    private val console = LoggerCollector()

    private val feignExporter get() = FeignClassExporter(project)
    private val actuatorExporter get() = ActuatorEndpointExporter(project)
    private val grpcExporter get() = GrpcClassExporter(project)

    override fun setUp() {
        super.setUp()
        loadFile("model/UserInfo.java")

        loadFile("feign/RequestLine.java")
        loadFile("spring/FeignClient.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("api/feign/generic/BaseClient.java")
        loadFile("api/feign/generic/UserClient.java")

        loadFile("spring/Endpoint.java")
        loadFile("spring/ReadOperation.java")
        loadFile("api/actuator/generic/GenericEndpointBase.java")
        loadFile("api/actuator/generic/UserActuatorEndpoint.java")

        loadFile("grpc/BindableService.java")
        loadFile("grpc/StreamObserver.java")
        loadFile("grpc/EchoRequest.java")
        loadFile("grpc/EchoResponse.java")
        loadFile("grpc/generic/GenericEchoService.java")
        loadFile("grpc/generic/GenericEchoServiceImpl.java")

        project.registerOrReplaceServiceInstance(
            IdeaConsoleProvider::class.java,
            mock<IdeaConsoleProvider> { on { getConsole() } doReturn console },
            testRootDisposable
        )
    }

    /** Event markers emitted by the rule scripts during this test. */
    private fun eventSequence(): List<String> =
        console.getMessages().map { it.message }.filter { it.startsWith("evt:") }

    private fun markers(prefix: String): List<String> =
        console.getMessages().map { it.message }.filter { it.startsWith(prefix) }

    private fun assertNoRawTypeLeak(events: List<String>) {
        assertTrue(
            "Lifecycle context must not expose the unsubstituted type parameter: $events",
            events.none {
                it.endsWith(":T") || it.contains(":T:") || it.contains("[T]")
            }
        )
    }

    /**
     * Both Feign branches fire `api.method.parse.before` / `api.method.parse.after`
     * and `export.after` with the inherited method; the context must report the
     * substituted `UserInfo`, not the base interface's `T`. Return and parameter
     * types alike: `nativeEcho`'s inherited `T item` parameter must report
     * `UserInfo` in both `argTypes()` and `args()[i].type()`.
     */
    fun testFeignLifecycleEventsUseResolvedGenericTypes() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.client.generic.UserClient")
        assertNotNull(psiClass)

        val endpoints = feignExporter.export(psiClass!!)
        assertEquals("two native + one spring mapping should each export an endpoint", 3, endpoints.size)

        val userInfo = "com.itangcent.model.UserInfo"
        assertEquals(
            listOf(
                "evt:method.before:nativeGet:$userInfo:[]:[]",
                "evt:method.after:nativeGet:$userInfo:[]:[]",
                "evt:export.after:nativeGet:$userInfo",
                "evt:method.before:springGet:$userInfo:[]:[]",
                "evt:method.after:springGet:$userInfo:[]:[]",
                "evt:export.after:springGet:$userInfo",
                "evt:method.before:nativeEcho:$userInfo:[$userInfo]:[$userInfo]",
                "evt:method.after:nativeEcho:$userInfo:[$userInfo]:[$userInfo]",
                "evt:export.after:nativeEcho:$userInfo"
            ).sorted(),
            eventSequence().sorted()
        )
        assertNoRawTypeLeak(eventSequence())
    }

    /**
     * Actuator exports have no method-level events; `export.after` is the only
     * lifecycle hook. The scanner only sees declared operations, so the inherited
     * generic `read()` must not reach the event, and the declared `current()`
     * reports its concrete return type. There is no type variable to substitute, so
     * this pins the event and its count, not generic substitution.
     */
    fun testActuatorExportAfterReportsDeclaredReturnType() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.controller.generic.UserActuatorEndpoint")
        assertNotNull(psiClass)

        val endpoints = actuatorExporter.export(psiClass!!)
        assertEquals(1, endpoints.size)

        assertEquals(
            listOf("evt:export.after:current:com.itangcent.model.UserInfo"),
            eventSequence()
        )
        assertTrue(
            "Inherited operations are not scanned, so `read` must not fire: ${eventSequence()}",
            eventSequence().none { it.startsWith("evt:export.after:read:") }
        )
        assertNoRawTypeLeak(eventSequence())
    }

    /**
     * gRPC exports also have no method-level events. The declared streaming method
     * returns `StreamObserver<EchoRequest>`, which `it.returnType()` must report.
     * There is no type variable to substitute, so this pins the event and its count,
     * not generic substitution.
     */
    fun testGrpcExportAfterReportsDeclaredReturnType() = runTest {
        val psiClass = findClass("com.itangcent.grpc.generic.GenericEchoServiceImpl")
        assertNotNull(psiClass)

        val endpoints = grpcExporter.export(psiClass!!)
        assertEquals(1, endpoints.size)

        assertEquals(
            listOf("evt:export.after:clientStream:io.grpc.stub.StreamObserver<com.itangcent.grpc.EchoRequest>"),
            eventSequence()
        )
        assertTrue(
            "Inherited operations are not scanned, so `echoStream` must not fire: ${eventSequence()}",
            eventSequence().none { it.startsWith("evt:export.after:echoStream:") }
        )
        assertNoRawTypeLeak(eventSequence())
    }

    /**
     * With a `ResolvedMethod` context, an event rule's `#regex:` filter matches the
     * return type. It used to match the method's whole source text on Feign, so a
     * filter on an annotation in that text no longer fires.
     */
    fun testFeignEventRegexFilterMatchesReturnTypeNotSource() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.client.generic.UserClient")
        assertNotNull(psiClass)

        feignExporter.export(psiClass!!)

        assertEquals(
            listOf("regex:type:nativeEcho", "regex:type:nativeGet", "regex:type:springGet"),
            markers("regex:type:").sorted()
        )
        assertEquals(emptyList<String>(), markers("regex:source:"))
    }

    /**
     * For an inherited method, `containingClass()` is the exported client and
     * `defineClass()` is the interface that declares the method.
     */
    fun testFeignEventContainingClassIsExportedClient() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.client.generic.UserClient")
        assertNotNull(psiClass)

        feignExporter.export(psiClass!!)

        val pkg = "com.itangcent.springboot.demo.client.generic"
        assertEquals(
            listOf("nativeEcho", "nativeGet", "springGet").map { "owner:$it:$pkg.UserClient:$pkg.BaseClient" },
            markers("owner:").sorted()
        )
    }

    override fun createConfigReader() = TestConfigReader.fromConfigText(
        project,
        """
        api.method.parse.before=groovy:logger.info("evt:method.before:" + it.name() + ":" + it.returnType().name() + ":" + it.argTypes()*.name() + ":" + it.args()*.type()*.name())
        api.method.parse.after=groovy:logger.info("evt:method.after:" + it.name() + ":" + it.returnType().name() + ":" + it.argTypes()*.name() + ":" + it.args()*.type()*.name())
        export.after=groovy:logger.info("evt:export.after:" + it.name() + ":" + it.returnType().name())
        export.after[#regex:^com.itangcent.model.UserInfo]=groovy:logger.info("regex:type:" + it.name())
        export.after[#regex:RequestLine]=groovy:logger.info("regex:source:" + it.name())
        api.method.parse.before[#regex:^com.itangcent.model.UserInfo]=groovy:logger.info("owner:"+ it.name() + ":" + it.containingClass().qualifiedName() + ":" + it.defineClass().qualifiedName())
        """.trimIndent()
    )
}
