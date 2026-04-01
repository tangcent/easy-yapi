package com.itangcent.easyapi.exporter.springmvc

import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class SpringMvcTitleExtractionTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporter: SpringMvcClassExporter

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        exporter = SpringMvcClassExporter(actionContext)
    }

    private fun loadTestFiles() {
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/RestController.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/TitleTestCtrl.java")
    }

    override fun createConfigReader() = TestConfigReader.EMPTY

    override fun customizeContext(builder: com.itangcent.easyapi.core.context.ActionContextBuilder) {
        builder.bind(DocHelper::class, StandardDocHelper())
    }

    fun testClassTitleFromDocComment() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")
        assertNotNull(psiClass)
        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should have endpoints", endpoints.isNotEmpty())
        val firstEndpoint = endpoints.first()
        assertEquals("User Management APIs", firstEndpoint.classDescription)
    }

    fun testMethodTitleFromDocComment() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")
        assertNotNull(psiClass)
        val endpoints = exporter.export(psiClass!!)
        val getUserEndpoint = endpoints.find { it.path == "/title-test/user/{id}" && it.method == HttpMethod.GET }
        assertNotNull("Should find GET endpoint", getUserEndpoint)
        assertEquals("Get user by ID", getUserEndpoint!!.name)
    }

    fun testMethodTitleFallbackToMethodName() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")
        assertNotNull(psiClass)
        val endpoints = exporter.export(psiClass!!)
        val noDocEndpoint = endpoints.find { it.path == "/title-test/no-doc" }
        assertNotNull("Should find no-doc endpoint", noDocEndpoint)
        assertEquals("noDocMethod", noDocEndpoint!!.name)
    }

    fun testClassTitleFallbackToClassName() = runTest {
        val psiClass = findClass("com.itangcent.api.NoClassDocCtrl")
        assertNotNull(psiClass)
        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should have endpoints", endpoints.isNotEmpty())
        val firstEndpoint = endpoints.first()
        assertEquals("NoClassDocCtrl", firstEndpoint.classDescription)
    }

    fun testAllEndpointsHaveClassDescription() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")
        assertNotNull(psiClass)
        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should have endpoints", endpoints.isNotEmpty())
        for (endpoint in endpoints) {
            assertNotNull("All endpoints should have classDescription", endpoint.classDescription)
            assertTrue("classDescription should not be blank", endpoint.classDescription?.isNotBlank() == true)
        }
    }

    fun testAllEndpointsHaveName() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")
        assertNotNull(psiClass)
        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should have endpoints", endpoints.isNotEmpty())
        for (endpoint in endpoints) {
            assertNotNull("All endpoints should have name", endpoint.name)
            assertTrue("name should not be blank", endpoint.name?.isNotBlank() == true)
        }
    }

    fun testMultilineDocComment() = runTest {
        val psiClass = findClass("com.itangcent.api.MultiLineDocCtrl")
        assertNotNull(psiClass)
        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should have endpoints", endpoints.isNotEmpty())
        val firstEndpoint = endpoints.first()
        assertNotNull("Method name should be extracted", firstEndpoint.name)
        assertTrue("Method name should not be blank", firstEndpoint.name?.isNotBlank() == true)
    }

    fun testEndpointHasSourceInfo() = runTest {
        val psiClass = findClass("com.itangcent.api.TitleTestCtrl")
        assertNotNull(psiClass)
        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should have endpoints", endpoints.isNotEmpty())
        for (endpoint in endpoints) {
            assertNotNull("All endpoints should have sourceClass", endpoint.sourceClass)
            assertNotNull("All endpoints should have sourceMethod", endpoint.sourceMethod)
            assertNotNull("All endpoints should have className", endpoint.className)
        }
    }
}
