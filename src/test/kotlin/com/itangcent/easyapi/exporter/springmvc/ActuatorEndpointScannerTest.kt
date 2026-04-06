package com.itangcent.easyapi.exporter.springmvc

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*

class ActuatorEndpointScannerTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var actuatorEndpointScanner: ActuatorEndpointScanner

    private lateinit var standardEndpointPsiClass: com.intellij.psi.PsiClass
    private lateinit var webAnnEndpointPsiClass: com.intellij.psi.PsiClass
    private lateinit var controllerAnnEndpointPsiClass: com.intellij.psi.PsiClass
    private lateinit var restControllerAnnEndpointPsiClass: com.intellij.psi.PsiClass

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        actuatorEndpointScanner = ActuatorEndpointScanner()
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

    fun testExportStandard() = runTest {
        val endpoints = actuatorEndpointScanner.scan(standardEndpointPsiClass)

        assertEquals(3, endpoints.size)

        endpoints[0].let { endpoint ->
            assertEquals("endpointByGet", endpoint.name)
            assertEquals(HttpMethod.GET, endpoint.httpMetadata?.method)
            assertEquals("/actuator/standard/{username}/{age}", endpoint.httpMetadata?.path)
            assertEquals(2, endpoint.httpMetadata?.parameters?.size)
            val pathParams = endpoint.httpMetadata?.parameters?.filter { it.binding == com.itangcent.easyapi.exporter.model.ParameterBinding.Path } ?: emptyList()
            assertEquals(2, pathParams.size)
            assertTrue(pathParams.any { it.name == "username" })
            assertTrue(pathParams.any { it.name == "age" })
        }

        endpoints[1].let { endpoint ->
            assertEquals("endpointByPost", endpoint.name)
            assertEquals(HttpMethod.POST, endpoint.httpMetadata?.method)
            assertEquals("/actuator/standard/{id}", endpoint.httpMetadata?.path)
            assertEquals(1, endpoint.httpMetadata?.parameters?.filter { it.binding == com.itangcent.easyapi.exporter.model.ParameterBinding.Path }?.size)
            assertNotNull(endpoint.httpMetadata?.body)
        }

        endpoints[2].let { endpoint ->
            assertEquals("endpointByDelete", endpoint.name)
            assertEquals(HttpMethod.DELETE, endpoint.httpMetadata?.method)
            assertEquals("/actuator/standard/{id}", endpoint.httpMetadata?.path)
            assertNotNull(endpoint.httpMetadata?.body)
        }
    }

    fun testExportWeb() = runTest {
        val endpoints = actuatorEndpointScanner.scan(webAnnEndpointPsiClass)

        assertEquals(3, endpoints.size)

        endpoints[0].let { endpoint ->
            assertEquals("endpointByGet", endpoint.name)
            assertEquals(HttpMethod.GET, endpoint.httpMetadata?.method)
            assertEquals("/actuator/web/{username}/{age}", endpoint.httpMetadata?.path)
            assertEquals(2, endpoint.httpMetadata?.parameters?.filter { it.binding == com.itangcent.easyapi.exporter.model.ParameterBinding.Path }?.size)
        }

        endpoints[1].let { endpoint ->
            assertEquals("endpointByPost", endpoint.name)
            assertEquals(HttpMethod.POST, endpoint.httpMetadata?.method)
            assertEquals("/actuator/web/{id}", endpoint.httpMetadata?.path)
            assertNotNull(endpoint.httpMetadata?.body)
        }

        endpoints[2].let { endpoint ->
            assertEquals("endpointByDelete", endpoint.name)
            assertEquals(HttpMethod.DELETE, endpoint.httpMetadata?.method)
            assertEquals("/actuator/web/{id}", endpoint.httpMetadata?.path)
            assertNotNull(endpoint.httpMetadata?.body)
        }
    }

    fun testExportController() = runTest {
        val endpoints = actuatorEndpointScanner.scan(controllerAnnEndpointPsiClass)

        assertEquals(3, endpoints.size)

        endpoints[0].let { endpoint ->
            assertEquals("endpointByGet", endpoint.name)
            assertEquals(HttpMethod.GET, endpoint.httpMetadata?.method)
            assertEquals("/actuator/controller/{username}/{age}", endpoint.httpMetadata?.path)
        }
    }

    fun testExportRestController() = runTest {
        val endpoints = actuatorEndpointScanner.scan(restControllerAnnEndpointPsiClass)

        assertEquals(3, endpoints.size)

        endpoints[0].let { endpoint ->
            assertEquals("endpointByGet", endpoint.name)
            assertEquals(HttpMethod.GET, endpoint.httpMetadata?.method)
            assertEquals("/actuator/rest/{username}/{age}", endpoint.httpMetadata?.path)
        }
    }
}
