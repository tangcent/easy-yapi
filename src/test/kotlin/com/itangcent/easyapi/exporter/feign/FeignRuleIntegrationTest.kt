package com.itangcent.easyapi.exporter.feign

import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class FeignRuleIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

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
        loadFile("spring/PutMapping.java")
        loadFile("spring/DeleteMapping.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/RestController.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/feign/UserClient.java")
    }

    override fun createConfigReader() = TestConfigReader.fromConfigText(
        project,
        """
        api.name=groovy:it.name().toUpperCase()
        folder.name=groovy:"feign-custom-folder"
        method.doc=groovy:"Rule doc: " + it.name()
        method.return=groovy:"com.itangcent.model.Result"
        method.return.main=groovy:"data"
        method.additional.header={"name":"X-Feign-Header","value":"feign-value","desc":"feign header from rule","required":true}
        method.additional.param={"name":"requestId","type":"String","required":true,"desc":"request id from rule","value":"default-req"}
        method.additional.response.header={"name":"X-Feign-Resp-Id","value":"feign-resp-123","desc":"feign response header from rule"}
        method.content.type=groovy:"application/json"
        """.trimIndent()
    )

    fun testRuleApiNameOverridesDefault() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.client.UserClient")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isNotEmpty())

        val greeting = endpoints.find { it.sourceMethod?.name == "greeting" }
        assertNotNull(greeting)
        assertEquals("GREETING", greeting!!.name)
    }

    fun testRuleFolderNameOverridesDefault() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.client.UserClient")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isNotEmpty())

        for (endpoint in endpoints) {
            assertEquals("feign-custom-folder", endpoint.folder)
        }
    }

    fun testRuleMethodDocAppended() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.client.UserClient")
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
        val psiClass = findClass("com.itangcent.springboot.demo.client.UserClient")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isNotEmpty())

        val addEndpoint = endpoints.find { it.sourceMethod?.name == "add" }
        assertNotNull(addEndpoint)
        assertNotNull(
            "Response body should be set from method.return rule",
            addEndpoint!!.httpMetadata?.responseBody
        )
    }

    fun testRuleAdditionalHeadersAdded() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.client.UserClient")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isNotEmpty())

        for (endpoint in endpoints) {
            val headers = endpoint.httpMetadata?.headers ?: emptyList()
            val customHeader = headers.find { it.name == "X-Feign-Header" }
            assertNotNull(
                "Each endpoint should have X-Feign-Header from rule",
                customHeader
            )
            assertEquals("feign-value", customHeader!!.value)
            assertEquals("feign header from rule", customHeader.description)
            assertTrue(customHeader.required)
        }
    }

    fun testRuleAdditionalParamsAdded() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.client.UserClient")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isNotEmpty())

        for (endpoint in endpoints) {
            val params = endpoint.httpMetadata?.parameters ?: emptyList()
            val reqParam = params.find { it.name == "requestId" }
            assertNotNull(
                "Each endpoint should have requestId param from rule",
                reqParam
            )
            assertEquals("default-req", reqParam!!.defaultValue)
            assertTrue(reqParam.required)
        }
    }

    fun testRuleAdditionalResponseHeadersAdded() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.client.UserClient")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isNotEmpty())

        for (endpoint in endpoints) {
            val headers = endpoint.httpMetadata?.headers ?: emptyList()
            val respHeader = headers.find { it.name == "X-Feign-Resp-Id" }
            assertNotNull(
                "Each endpoint should have X-Feign-Resp-Id header from rule",
                respHeader
            )
            assertEquals("feign-resp-123", respHeader!!.value)
        }
    }

    fun testRuleContentTypeOverride() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.client.UserClient")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isNotEmpty())

        val addEndpoint = endpoints.find { it.sourceMethod?.name == "add" }
        assertNotNull(addEndpoint)
        assertEquals(
            "application/json",
            addEndpoint!!.httpMetadata?.contentType
        )
    }
}
