package com.itangcent.easyapi.exporter.lifecycle

import com.itangcent.easyapi.exporter.ClassExporter
import com.itangcent.easyapi.exporter.feign.FeignClassExporter
import com.itangcent.easyapi.exporter.grpc.GrpcClassExporter
import com.itangcent.easyapi.exporter.jaxrs.JaxRsClassExporter
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.springmvc.ActuatorEndpointExporter
import com.itangcent.easyapi.exporter.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.UnifiedDocHelper
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class ApiLifecycleEventsTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var springExporter: SpringMvcClassExporter
    private lateinit var jaxRsExporter: JaxRsClassExporter
    private lateinit var feignExporter: FeignClassExporter
    private lateinit var grpcExporter: GrpcClassExporter
    private lateinit var actuatorExporter: ActuatorEndpointExporter

    override fun setUp() {
        super.setUp()
        loadCommonTestFiles()
        springExporter = SpringMvcClassExporter(project)
        jaxRsExporter = JaxRsClassExporter(project)
        feignExporter = FeignClassExporter(project)
        grpcExporter = GrpcClassExporter(project)
        actuatorExporter = ActuatorEndpointExporter(project)
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
        api.class.parse.before=groovy:logger.info("lifecycle:api.class.parse.before:" + it.name())
        api.class.parse.after=groovy:logger.info("lifecycle:api.class.parse.after:" + it.name())
        api.method.parse.before=groovy:logger.info("lifecycle:api.method.parse.before:" + it.name())
        api.method.parse.after=groovy:logger.info("lifecycle:api.method.parse.after:" + it.name())
        export.after=groovy:logger.info("lifecycle:export.after:" + it.name())
        """.trimIndent()
    )


    // ── Spring MVC lifecycle events ──────────────────────────────

    fun testSpringMvcClassParseEvents() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = springExporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())
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
    }
}
