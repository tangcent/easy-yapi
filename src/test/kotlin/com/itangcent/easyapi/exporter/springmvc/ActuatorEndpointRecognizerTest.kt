package com.itangcent.easyapi.exporter.springmvc

import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class ActuatorEndpointRecognizerTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var recognizer: ActuatorEndpointRecognizer

    override fun setUp() {
        super.setUp()
        loadAnnotationStubs()
        recognizer = ActuatorEndpointRecognizer()
    }

    private fun loadAnnotationStubs() {
        loadFile("spring/Endpoint.java")
        loadFile("spring/WebEndpoint.java")
        loadFile("spring/ControllerEndpoint.java")
        loadFile("spring/RestControllerEndpoint.java")
        loadFile("spring/ReadOperation.java")
        loadFile("spring/WriteOperation.java")
        loadFile("spring/DeleteOperation.java")
        loadFile("spring/Selector.java")
        loadFile("spring/PostMapping.java")
    }

    override fun createConfigReader() = TestConfigReader.EMPTY


    fun testRecognizesStandardEndpoint() = runTest {
        loadFile("api/actuator/StandardEndpoint.java")
        val psiClass = findClass("com.itangcent.springboot.demo.controller.StandardEndpoint")!!
        assertTrue(recognizer.isApiClass(psiClass))
    }

    fun testRecognizesWebEndpoint() = runTest {
        loadFile("api/actuator/WebAnnEndpoint.java")
        val psiClass = findClass("com.itangcent.springboot.demo.controller.WebAnnEndpoint")!!
        assertTrue(recognizer.isApiClass(psiClass))
    }

    fun testRecognizesControllerEndpoint() = runTest {
        loadFile("api/actuator/ControllerAnnEndpoint.java")
        val psiClass = findClass("com.itangcent.springboot.demo.controller.ControllerAnnEndpoint")!!
        assertTrue(recognizer.isApiClass(psiClass))
    }

    fun testRecognizesRestControllerEndpoint() = runTest {
        loadFile("api/actuator/RestControllerAnnEndpoint.java")
        val psiClass = findClass("com.itangcent.springboot.demo.controller.RestControllerAnnEndpoint")!!
        assertTrue(recognizer.isApiClass(psiClass))
    }

    fun testRejectsPlainClass() = runTest {
        loadFile(
            "api/PlainService.java",
            """
            package com.itangcent.springboot.demo.service;
            public class PlainService {
                public String hello() { return "hello"; }
            }
            """.trimIndent()
        )
        val psiClass = findClass("com.itangcent.springboot.demo.service.PlainService")!!
        assertFalse(recognizer.isApiClass(psiClass))
    }

    fun testDisabledRecognizerRejectsAll() = runTest {
        val disabled = ActuatorEndpointRecognizer(enabled = false)
        loadFile("api/actuator/StandardEndpoint.java")
        val psiClass = findClass("com.itangcent.springboot.demo.controller.StandardEndpoint")!!
        assertFalse(disabled.isApiClass(psiClass))
    }

    fun testFrameworkName() {
        assertEquals("SpringActuator", recognizer.frameworkName)
    }

    fun testTargetAnnotations() {
        assertEquals(SpringActuatorConstants.ENDPOINT_ANNOTATIONS, recognizer.targetAnnotations)
    }
}
