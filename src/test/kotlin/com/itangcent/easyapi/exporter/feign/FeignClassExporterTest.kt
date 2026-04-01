package com.itangcent.easyapi.exporter.feign

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper

class FeignClassExporterTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporter: FeignClassExporter

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        exporter = FeignClassExporter(actionContext, feignEnable = true)
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

    override fun createConfigReader() = TestConfigReader.EMPTY

    override fun customizeContext(builder: com.itangcent.easyapi.core.context.ActionContextBuilder) {
        builder.bind(DocHelper::class, StandardDocHelper())
    }

    fun testExportFeignClient() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.client.UserClient")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isNotEmpty())
    }

    fun testExportGetMapping() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.client.UserClient")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val getEndpoints = endpoints.filter { it.method == HttpMethod.GET }
        assertTrue(getEndpoints.isNotEmpty())
    }

    fun testExportPostMapping() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.client.UserClient")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val postEndpoints = endpoints.filter { it.method == HttpMethod.POST }
        assertTrue(postEndpoints.isNotEmpty())
    }

    fun testExportWithPathVariable() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.client.UserClient")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val getEndpoint = endpoints.find { it.path.contains("{id}") }
        assertNotNull(getEndpoint)
        assertTrue(getEndpoint!!.parameters.any { it.binding == com.itangcent.easyapi.exporter.model.ParameterBinding.Path })
    }

    fun testExportWithRequestBody() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.client.UserClient")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val postEndpoint = endpoints.find { it.method == HttpMethod.POST }
        assertNotNull(postEndpoint)
        assertTrue(postEndpoint!!.parameters.any { it.binding == com.itangcent.easyapi.exporter.model.ParameterBinding.Body })
    }

    fun testExportNonFeignClient() = runTest {
        val psiClass = findClass("com.itangcent.model.UserInfo")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isEmpty())
    }
}
