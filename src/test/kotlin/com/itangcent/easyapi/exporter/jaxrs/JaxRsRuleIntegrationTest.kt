package com.itangcent.easyapi.exporter.jaxrs

import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class JaxRsRuleIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

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
        loadFile("api/jaxrs/MyGet.java")
        loadFile("api/jaxrs/MyPut.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("constant/UserType.java")
        loadFile("api/jaxrs/UserResource.java")
        loadFile("api/jaxrs/UserDTO.java")
    }

    override fun createConfigReader() = TestConfigReader.fromConfigText(
        project,
        """
        api.name=groovy:it.name().toUpperCase()
        folder.name=groovy:"jaxrs-custom-folder"
        method.doc=groovy:"Rule doc: " + it.name()
        method.return=groovy:"com.itangcent.model.Result"
        method.return.main=groovy:"data"
        method.additional.header={"name":"X-Custom-Header","value":"custom-value","desc":"custom header from rule","required":true}
        method.additional.param={"name":"traceId","type":"String","required":true,"desc":"trace id from rule","value":"default-trace"}
        method.additional.response.header={"name":"X-Response-Id","value":"resp-123","desc":"response header from rule"}
        method.default.http.method=groovy:"POST"
        """.trimIndent()
    )

    fun testRuleApiNameOverridesDefault() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isNotEmpty())

        val greeting = endpoints.find { it.sourceMethod?.name == "greeting" }
        assertNotNull(greeting)
        assertEquals("GREETING", greeting!!.name)
    }

    fun testRuleFolderNameOverridesDefault() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isNotEmpty())

        for (endpoint in endpoints) {
            assertEquals("jaxrs-custom-folder", endpoint.folder)
        }
    }

    fun testRuleMethodDocAppended() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isNotEmpty())

        val greeting = endpoints.find { it.sourceMethod?.name == "greeting" }
        assertNotNull(greeting)
        assertNotNull(greeting!!.description)
        assertTrue(
            "Description should contain rule-based doc",
            greeting.description!!.contains("Rule doc:")
        )
    }

    fun testRuleMethodReturnOverridesType() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isNotEmpty())

        val typesEndpoint = endpoints.find { it.sourceMethod?.name == "types" }
        assertNotNull(typesEndpoint)
        assertNotNull("Response body should be set from method.return rule", typesEndpoint!!.httpMetadata?.responseBody)
    }

    fun testRuleAdditionalHeadersAdded() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isNotEmpty())

        for (endpoint in endpoints) {
            val headers = endpoint.httpMetadata?.headers ?: emptyList()
            val customHeader = headers.find { it.name == "X-Custom-Header" }
            assertNotNull(
                "Each endpoint should have X-Custom-Header from rule",
                customHeader
            )
            assertEquals("custom-value", customHeader!!.value)
            assertEquals("custom header from rule", customHeader.description)
            assertTrue(customHeader.required)
        }
    }

    fun testRuleAdditionalParamsAdded() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isNotEmpty())

        for (endpoint in endpoints) {
            val params = endpoint.httpMetadata?.parameters ?: emptyList()
            val traceParam = params.find { it.name == "traceId" }
            assertNotNull(
                "Each endpoint should have traceId param from rule",
                traceParam
            )
            assertEquals("default-trace", traceParam!!.defaultValue)
            assertTrue(traceParam.required)
        }
    }

    fun testRuleAdditionalResponseHeadersAdded() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isNotEmpty())

        for (endpoint in endpoints) {
            val headers = endpoint.httpMetadata?.headers ?: emptyList()
            val respHeader = headers.find { it.name == "X-Response-Id" }
            assertNotNull(
                "Each endpoint should have X-Response-Id header from rule",
                respHeader
            )
            assertEquals("resp-123", respHeader!!.value)
        }
    }

    fun testRuleDefaultHttpMethodNotOverrideExisting() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isNotEmpty())

        val getEndpoints = endpoints.filter { it.httpMetadata?.method == HttpMethod.GET }
        assertTrue("Should have GET endpoints from @GET annotation", getEndpoints.isNotEmpty())

        val postEndpoints = endpoints.filter { it.httpMetadata?.method == HttpMethod.POST }
        assertTrue("Should have POST endpoints from @POST annotation", postEndpoints.isNotEmpty())
    }
}
