package com.itangcent.easyapi.exporter.jaxrs

import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.model.path
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class JaxRsTitleExtractionTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporter: JaxRsClassExporter

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        exporter = JaxRsClassExporter(project)
    }

    private fun loadTestFiles() {
        loadFile("jaxrs/Path.java")
        loadFile("jaxrs/GET.java")
        loadFile("jaxrs/POST.java")
        loadFile("jaxrs/PUT.java")
        loadFile("jaxrs/DELETE.java")
        loadFile("jaxrs/PathParam.java")
        loadFile("jaxrs/QueryParam.java")
        loadFile("jaxrs/FormParam.java")
        loadFile("jaxrs/HeaderParam.java")
        loadFile("jaxrs/Produces.java")
        loadFile("jaxrs/Consumes.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/jaxrs/TitleTestResource.java")
    }

    override fun createConfigReader() = TestConfigReader.empty(project)


    fun testClassTitleFromDocComment() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.TitleTestResource")
        assertNotNull(psiClass)
        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should have endpoints", endpoints.isNotEmpty())
        val firstEndpoint = endpoints.first()
        assertEquals("Product Resource APIs", firstEndpoint.classDescription)
    }

    fun testMethodTitleFromDocComment() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.TitleTestResource")
        assertNotNull(psiClass)
        val endpoints = exporter.export(psiClass!!)
        val getEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull("Should find GET endpoint", getEndpoint)
        assertEquals("Get all products", getEndpoint!!.name)
    }

    fun testMethodTitleFallbackToMethodName() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.TitleTestResource")
        assertNotNull(psiClass)
        val endpoints = exporter.export(psiClass!!)
        val noDocEndpoint = endpoints.find { it.path == "/title-test/no-doc" }
        assertNotNull("Should find no-doc endpoint", noDocEndpoint)
        assertEquals("noDocMethod", noDocEndpoint!!.name)
    }

    fun testAllEndpointsHaveClassDescription() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.TitleTestResource")
        assertNotNull(psiClass)
        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should have endpoints", endpoints.isNotEmpty())
        for (endpoint in endpoints) {
            assertNotNull("All endpoints should have classDescription", endpoint.classDescription)
            assertTrue("classDescription should not be blank", endpoint.classDescription?.isNotBlank() == true)
        }
    }

    fun testAllEndpointsHaveName() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.TitleTestResource")
        assertNotNull(psiClass)
        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should have endpoints", endpoints.isNotEmpty())
        for (endpoint in endpoints) {
            assertNotNull("All endpoints should have name", endpoint.name)
            assertTrue("name should not be blank", endpoint.name?.isNotBlank() == true)
        }
    }

    fun testEndpointHasSourceInfo() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.TitleTestResource")
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
