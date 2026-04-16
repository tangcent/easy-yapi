package com.itangcent.easyapi.integration

import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class FullExportIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporter: SpringMvcClassExporter

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        exporter = SpringMvcClassExporter(project)
    }

    private fun loadTestFiles() {
        loadFile("org/springframework/stereotype/Component.java")
        loadFile("org/springframework/stereotype/Controller.java")
        loadFile("spring/RestController.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/PutMapping.java")
        loadFile("spring/DeleteMapping.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/UserCtrl.java")
    }

    override fun createConfigReader() = TestConfigReader.empty(project)


    fun testFullExportPipeline() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull("Should find UserCtrl class", psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should export at least one endpoint", endpoints.isNotEmpty())

        val endpoint = endpoints.first()
        assertNotNull("Endpoint should have name", endpoint.name)
        assertNotNull("Endpoint should have path", endpoint.httpMetadata?.path)
        assertNotNull("Endpoint should have method", endpoint.httpMetadata?.method)

        val json5Output = endpoint.name + " " + endpoint.httpMetadata?.path + " " + endpoint.httpMetadata?.method?.name
        assertTrue("Output should contain endpoint info", json5Output.isNotEmpty())
    }

    fun testExportMultipleEndpoints() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should export multiple endpoints", endpoints.size >= 1)

        val methods = endpoints.mapNotNull { it.httpMetadata?.method }.distinct()
        assertTrue("Should have different HTTP methods", methods.isNotEmpty())
    }

    fun testExportWithParameters() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val endpointsWithParams = endpoints.filter { (it.httpMetadata?.parameters ?: emptyList()).isNotEmpty() }
        assertTrue("Should have endpoints with parameters", endpointsWithParams.isNotEmpty())

        val endpoint = endpointsWithParams.first()
        val param = endpoint.httpMetadata!!.parameters.first()
        assertNotNull("Parameter should have name", param.name)
    }
}
