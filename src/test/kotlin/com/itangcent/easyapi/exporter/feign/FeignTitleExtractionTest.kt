package com.itangcent.easyapi.exporter.feign

import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.model.path
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class FeignTitleExtractionTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporter: FeignClassExporter

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        exporter = FeignClassExporter(project, feignEnable = true)
    }

    private fun loadTestFiles() {
        loadFile("spring/FeignClient.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/feign/TitleTestClient.java")
    }

    override fun createConfigReader() = TestConfigReader.empty(project)


    fun testClassTitleFromDocComment() = runTest {
        val psiClass = findClass("com.itangcent.client.TitleTestClient")
        assertNotNull(psiClass)
        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should have endpoints", endpoints.isNotEmpty())
        val firstEndpoint = endpoints.first()
        assertEquals("Order Service Client", firstEndpoint.classDescription)
    }

    fun testMethodTitleFromDocComment() = runTest {
        val psiClass = findClass("com.itangcent.client.TitleTestClient")
        assertNotNull(psiClass)
        val endpoints = exporter.export(psiClass!!)
        val getEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull("Should find GET endpoint", getEndpoint)
        assertEquals("Get order by ID", getEndpoint!!.name)
    }

    fun testMethodTitleFallbackToMethodName() = runTest {
        val psiClass = findClass("com.itangcent.client.TitleTestClient")
        assertNotNull(psiClass)
        val endpoints = exporter.export(psiClass!!)
        val noDocEndpoint = endpoints.find { it.path == "/title-test/no-doc" }
        assertNotNull("Should find no-doc endpoint", noDocEndpoint)
        assertEquals("noDocMethod", noDocEndpoint!!.name)
    }

    fun testAllEndpointsHaveClassDescription() = runTest {
        val psiClass = findClass("com.itangcent.client.TitleTestClient")
        assertNotNull(psiClass)
        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should have endpoints", endpoints.isNotEmpty())
        for (endpoint in endpoints) {
            assertNotNull("All endpoints should have classDescription", endpoint.classDescription)
            assertTrue("classDescription should not be blank", endpoint.classDescription?.isNotBlank() == true)
        }
    }

    fun testAllEndpointsHaveName() = runTest {
        val psiClass = findClass("com.itangcent.client.TitleTestClient")
        assertNotNull(psiClass)
        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should have endpoints", endpoints.isNotEmpty())
        for (endpoint in endpoints) {
            assertNotNull("All endpoints should have name", endpoint.name)
            assertTrue("name should not be blank", endpoint.name?.isNotBlank() == true)
        }
    }

    fun testEndpointHasSourceInfo() = runTest {
        val psiClass = findClass("com.itangcent.client.TitleTestClient")
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
