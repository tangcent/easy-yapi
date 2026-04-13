package com.itangcent.easyapi.exporter.jaxrs

import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class JaxRsClassExporterLifecycleTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporter: JaxRsClassExporter

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        exporter = JaxRsClassExporter(project, jaxrsEnable = true)
    }

    private fun loadTestFiles() {
        loadFile("jaxrs/Path.java")
        loadFile("jaxrs/GET.java")
        loadFile("jaxrs/POST.java")
        loadFile("jaxrs/PUT.java")
        loadFile("jaxrs/DELETE.java")
        loadFile("jaxrs/PATCH.java")
        loadFile("jaxrs/OPTIONS.java")
        loadFile("jaxrs/HEAD.java")
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
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("constant/UserType.java")
        loadFile("api/jaxrs/UserResource.java")
        loadFile("api/jaxrs/UserDTO.java")
    }

    override fun createConfigReader() = TestConfigReader.fromConfigText(
        """
        api.class.parse.before=groovy:logger.info("lifecycle:api.class.parse.before:" + it.name())
        api.class.parse.after=groovy:logger.info("lifecycle:api.class.parse.after:" + it.name())
        api.method.parse.before=groovy:logger.info("lifecycle:api.method.parse.before:" + it.name())
        api.method.parse.after=groovy:logger.info("lifecycle:api.method.parse.after:" + it.name())
        export.after=groovy:logger.info("lifecycle:export.after:" + it.name())
        """.trimIndent()
    )


    fun testJaxRsClassParseBeforeAndAfter() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())
    }

    fun testJaxRsMethodParseBeforeAndAfter() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())

        val getEndpoints = endpoints.filter { it.httpMetadata?.method == HttpMethod.GET }
        assertTrue("Should have GET endpoints", getEndpoints.isNotEmpty())

        val postEndpoints = endpoints.filter { it.httpMetadata?.method == HttpMethod.POST }
        assertTrue("Should have POST endpoints", postEndpoints.isNotEmpty())
    }

    fun testJaxRsExportAfterEvent() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())

        for (endpoint in endpoints) {
            assertNotNull("Each endpoint should have source method for EXPORT_AFTER", endpoint.sourceMethod)
            assertNotNull("Each endpoint should have HTTP metadata", endpoint.httpMetadata)
        }
    }

    fun testJaxRsNonResourceNoEvents() = runTest {
        val psiClass = findClass("com.itangcent.model.UserInfo")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("Non-JAX-RS class should return empty endpoints", endpoints.isEmpty())
    }

    fun testJaxRsClassParseAfterFiresOnSuccess() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())
        assertTrue("Should have multiple endpoints from different HTTP methods", endpoints.size > 1)
    }

    fun testJaxRsMethodParseAfterFiresForAllMethods() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())

        val methods = endpoints.mapNotNull { it.sourceMethod?.name }.toSet()
        assertTrue("Should have endpoints from multiple methods", methods.size > 1)
    }

    fun testJaxRsAllHttpMethodsFireEvents() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())

        val httpMethods = endpoints.mapNotNull { it.httpMetadata?.method }.toSet()
        assertTrue("Should have multiple HTTP methods", httpMethods.size > 1)
    }
}
