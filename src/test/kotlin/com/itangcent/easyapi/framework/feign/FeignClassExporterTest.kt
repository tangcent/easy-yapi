package com.itangcent.easyapi.framework.feign

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.export.path
import com.itangcent.easyapi.core.psi.helper.DocHelper
import com.itangcent.easyapi.core.psi.helper.UnifiedDocHelper

class FeignClassExporterTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporter: FeignClassExporter

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        exporter = FeignClassExporter(project)
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

    override fun createConfigReader() = TestConfigReader.empty(project)


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
        val getEndpoints = endpoints.filter { it.httpMetadata?.method == HttpMethod.GET }
        assertTrue(getEndpoints.isNotEmpty())
    }

    fun testExportPostMapping() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.client.UserClient")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val postEndpoints = endpoints.filter { it.httpMetadata?.method == HttpMethod.POST }
        assertTrue(postEndpoints.isNotEmpty())
    }

    fun testExportWithPathVariable() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.client.UserClient")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val getEndpoint = endpoints.find { it.path.contains("{id}") }
        assertNotNull(getEndpoint)
        assertTrue(getEndpoint!!.httpMetadata!!.parameters.any { it.binding == com.itangcent.easyapi.core.export.ParameterBinding.Path })
    }

    fun testExportWithRequestBody() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.client.UserClient")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val postEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.POST }
        assertNotNull(postEndpoint)
        assertNotNull("POST endpoint should have a request body", postEndpoint!!.httpMetadata!!.body)
    }

    fun testSpringStyleParametersExportAnnotationNames() = runTest {
        loadFile(
            "api/feign/OrderClient.java",
            """
            package com.itangcent.springboot.demo.client;
            import com.itangcent.model.Result;
            import org.springframework.cloud.openfeign.FeignClient;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.PathVariable;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RequestParam;

            @FeignClient("orders")
            @RequestMapping(value = "/orders")
            public interface OrderClient {

                @GetMapping("/{id}")
                Result<String> get(@PathVariable("id") Long orderId,
                                   @RequestParam(name = "with_items", required = false) Boolean withItems);
            }
            """.trimIndent()
        )
        val psiClass = findClass("com.itangcent.springboot.demo.client.OrderClient")
        assertNotNull(psiClass)

        val endpoint = exporter.export(psiClass!!).singleOrNull { it.path.endsWith("/orders/{id}") }
        assertNotNull("Should export /orders/{id}", endpoint)
        val params = endpoint!!.httpMetadata!!.parameters
        val dump = params.joinToString { "${it.name}(${it.binding})" }
        assertEquals(
            "the {id} placeholder should be exported once, under its own name; actual=[$dump]",
            listOf("id"),
            params.filter { it.binding == com.itangcent.easyapi.core.export.ParameterBinding.Path }.map { it.name }
        )
        assertTrue("with_items should be exported; actual=[$dump]", params.any { it.name == "with_items" })
        assertFalse("withItems must not be exported; actual=[$dump]", params.any { it.name == "withItems" })
    }

    fun testExportNonFeignClient() = runTest {
        val psiClass = findClass("com.itangcent.model.UserInfo")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isEmpty())
    }
}
