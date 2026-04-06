package com.itangcent.easyapi.exporter.springmvc

import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.model.path
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class ActuatorEndpointExporterTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporter: ActuatorEndpointExporter

    private lateinit var standardEndpointPsiClass: com.intellij.psi.PsiClass
    private lateinit var webAnnEndpointPsiClass: com.intellij.psi.PsiClass
    private lateinit var controllerAnnEndpointPsiClass: com.intellij.psi.PsiClass
    private lateinit var restControllerAnnEndpointPsiClass: com.intellij.psi.PsiClass

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        exporter = ActuatorEndpointExporter()
    }

    private fun loadTestFiles() {
        loadFile("spring/Endpoint.java")
        loadFile("spring/WebEndpoint.java")
        loadFile("spring/ControllerEndpoint.java")
        loadFile("spring/RestControllerEndpoint.java")
        loadFile("spring/ReadOperation.java")
        loadFile("spring/WriteOperation.java")
        loadFile("spring/DeleteOperation.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/Selector.java")
        loadFile("api/actuator/StandardEndpoint.java")
        loadFile("api/actuator/WebAnnEndpoint.java")
        loadFile("api/actuator/ControllerAnnEndpoint.java")
        loadFile("api/actuator/RestControllerAnnEndpoint.java")
        standardEndpointPsiClass = findClass("com.itangcent.springboot.demo.controller.StandardEndpoint")!!
        webAnnEndpointPsiClass = findClass("com.itangcent.springboot.demo.controller.WebAnnEndpoint")!!
        controllerAnnEndpointPsiClass = findClass("com.itangcent.springboot.demo.controller.ControllerAnnEndpoint")!!
        restControllerAnnEndpointPsiClass = findClass("com.itangcent.springboot.demo.controller.RestControllerAnnEndpoint")!!
    }

    override fun createConfigReader() = TestConfigReader.EMPTY

    override fun customizeContext(builder: com.itangcent.easyapi.core.context.ActionContextBuilder) {
        builder.bind(DocHelper::class, StandardDocHelper())
    }

    // --- Standard @Endpoint ---

    fun testExportStandardEndpoint() = runTest {
        val endpoints = exporter.export(standardEndpointPsiClass)
        assertEquals(3, endpoints.size)
    }

    fun testStandardReadOperation() = runTest {
        val endpoints = exporter.export(standardEndpointPsiClass)
        val readOp = endpoints.first { it.name == "endpointByGet" }

        assertEquals(HttpMethod.GET, readOp.httpMetadata?.method)
        assertEquals("/actuator/standard/{username}/{age}", readOp.path)
        val pathParams = readOp.httpMetadata?.parameters?.filter { it.binding == ParameterBinding.Path } ?: emptyList()
        assertEquals(2, pathParams.size)
        assertTrue(pathParams.any { it.name == "username" })
        assertTrue(pathParams.any { it.name == "age" })
    }

    fun testStandardWriteOperation() = runTest {
        val endpoints = exporter.export(standardEndpointPsiClass)
        val writeOp = endpoints.first { it.name == "endpointByPost" }

        assertEquals(HttpMethod.POST, writeOp.httpMetadata?.method)
        assertEquals("/actuator/standard/{id}", writeOp.path)
        assertEquals(1, writeOp.httpMetadata?.parameters?.filter { it.binding == ParameterBinding.Path }?.size)
        assertNotNull(writeOp.httpMetadata?.body)
    }

    fun testStandardDeleteOperation() = runTest {
        val endpoints = exporter.export(standardEndpointPsiClass)
        val deleteOp = endpoints.first { it.name == "endpointByDelete" }

        assertEquals(HttpMethod.DELETE, deleteOp.httpMetadata?.method)
        assertEquals("/actuator/standard/{id}", deleteOp.path)
        assertNotNull(deleteOp.httpMetadata?.body)
    }

    // --- @WebEndpoint ---

    fun testExportWebEndpoint() = runTest {
        val endpoints = exporter.export(webAnnEndpointPsiClass)
        assertEquals(3, endpoints.size)

        val readOp = endpoints.first { it.name == "endpointByGet" }
        assertEquals(HttpMethod.GET, readOp.httpMetadata?.method)
        assertEquals("/actuator/web/{username}/{age}", readOp.path)
    }

    // --- @ControllerEndpoint ---

    fun testExportControllerEndpoint() = runTest {
        val endpoints = exporter.export(controllerAnnEndpointPsiClass)
        assertEquals(3, endpoints.size)

        val readOp = endpoints.first { it.name == "endpointByGet" }
        assertEquals(HttpMethod.GET, readOp.httpMetadata?.method)
        assertEquals("/actuator/controller/{username}/{age}", readOp.path)
    }

    // --- @RestControllerEndpoint ---

    fun testExportRestControllerEndpoint() = runTest {
        val endpoints = exporter.export(restControllerAnnEndpointPsiClass)
        assertEquals(3, endpoints.size)

        val readOp = endpoints.first { it.name == "endpointByGet" }
        assertEquals(HttpMethod.GET, readOp.httpMetadata?.method)
        assertEquals("/actuator/rest/{username}/{age}", readOp.path)
    }

    // --- Negative cases ---

    fun testExportPlainClassReturnsEmpty() = runTest {
        loadFile(
            "api/PlainController.java",
            """
            package com.itangcent.springboot.demo.controller;
            public class PlainController {
                public String index() { return "ok"; }
            }
            """.trimIndent()
        )
        val psiClass = findClass("com.itangcent.springboot.demo.controller.PlainController")!!
        val endpoints = exporter.export(psiClass)
        assertTrue(endpoints.isEmpty())
    }
}
