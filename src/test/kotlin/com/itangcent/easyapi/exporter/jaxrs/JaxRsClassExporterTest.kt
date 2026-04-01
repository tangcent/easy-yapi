package com.itangcent.easyapi.exporter.jaxrs

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper

class JaxRsClassExporterTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporter: JaxRsClassExporter

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        exporter = JaxRsClassExporter(actionContext, jaxrsEnable = true)
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
        loadFile("jaxrs/CookieParam.java")
        loadFile("jaxrs/BeanParam.java")
        loadFile("jaxrs/DefaultValue.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("constant/UserType.java")
        loadFile("api/jaxrs/UserResource.java")
        loadFile("api/jaxrs/UserDTO.java")
    }

    override fun createConfigReader() = TestConfigReader.EMPTY

    override fun customizeContext(builder: com.itangcent.easyapi.core.context.ActionContextBuilder) {
        builder.bind(DocHelper::class, StandardDocHelper())
    }

    fun testExportJaxRsResource() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isNotEmpty())
    }

    fun testExportGetMethod() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val getEndpoints = endpoints.filter { it.method == HttpMethod.GET }
        assertTrue(getEndpoints.isNotEmpty())
    }

    fun testExportPostMethod() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val postEndpoints = endpoints.filter { it.method == HttpMethod.POST }
        assertTrue(postEndpoints.isNotEmpty())
    }

    fun testExportPutMethod() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val putEndpoints = endpoints.filter { it.method == HttpMethod.PUT }
        assertTrue(putEndpoints.isNotEmpty())
    }

    fun testExportDeleteMethod() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val deleteEndpoints = endpoints.filter { it.method == HttpMethod.DELETE }
        assertTrue(deleteEndpoints.isNotEmpty())
    }

    fun testExportWithPathParam() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val endpointWithPathParam = endpoints.find { it.path.contains("{id}") }
        assertNotNull(endpointWithPathParam)
    }

    fun testExportNonResource() = runTest {
        val psiClass = findClass("com.itangcent.model.UserInfo")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isEmpty())
    }
}
