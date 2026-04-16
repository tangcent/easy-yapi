package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class SpringConfigIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporter: SpringMvcClassExporter

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        exporter = SpringMvcClassExporter(project)
    }

    private fun loadTestFiles() {
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/PutMapping.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/RestController.java")
        loadFile("spring/Controller.java")
        loadFile("model/Result.java")
        loadFile("model/IResult.java")
        loadFile("model/UserInfo.java")
        loadFile("org/springframework/http/HttpEntity.java")
        loadFile("org/springframework/http/RequestEntity.java")
        loadFile("org/springframework/http/ResponseEntity.java")
        loadFile("org/springframework/http/HttpStatus.java")
        loadFile("org/springframework/http/HttpHeaders.java")
        loadFile("org/springframework/web/context/request/async/DeferredResult.java")
        loadFile("api/spring/EntityController.java")
    }

    override fun createConfigReader(): TestConfigReader {
        val extension = ExtensionConfigRegistry.getExtension("spring")
        assertNotNull("spring extension should exist", extension)
        val content = extension?.content ?: ""
        assertTrue("Extension content should not be blank", content.isNotBlank())
        return TestConfigReader.fromConfigText(project, content)
    }


    fun testSpringConfigLoadsCorrectly() = runTest {
        val extension = ExtensionConfigRegistry.getExtension("spring")
        assertNotNull("spring extension should exist", extension)
        assertEquals("Extension code should be spring", "spring", extension?.code)
        assertTrue("Extension should have content", extension?.content?.isNotBlank() == true)
        
        val configReader = createConfigReader()
        var hasConvertRule = false
        configReader.foreach({ it.startsWith("json.rule.convert") }) { _, _ -> hasConvertRule = true }
        assertTrue("json.rule.convert rules should exist", hasConvertRule)
    }

    fun testEntityControllerExportsEndpoints() = runTest {
        val psiClass = findClass("com.itangcent.spring.entity.EntityController")
        assertNotNull("Should find EntityController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertEquals("Should export 5 endpoints", 5, endpoints.size)
    }

    fun testResponseEntityIsUnwrapped() = runTest {
        val psiClass = findClass("com.itangcent.spring.entity.EntityController")
        assertNotNull("Should find EntityController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        
        val getUserEndpoint = endpoints.find { it.httpMetadata?.path == "/entity/user/{id}" && it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull("Should find GET /entity/user/{id} endpoint", getUserEndpoint)
        
        val responseBody = getUserEndpoint!!.httpMetadata?.responseBody
        assertNotNull("Response body should not be null", responseBody)
    }

    fun testDeferredResultIsUnwrapped() = runTest {
        val psiClass = findClass("com.itangcent.spring.entity.EntityController")
        assertNotNull("Should find EntityController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        
        val asyncEndpoint = endpoints.find { it.httpMetadata?.path == "/entity/async/{id}" }
        assertNotNull("Should find async endpoint", asyncEndpoint)
        
        val responseBody = asyncEndpoint!!.httpMetadata?.responseBody
        assertNotNull("Response body should not be null", responseBody)
    }

    fun testHttpEntityIsUnwrapped() = runTest {
        val psiClass = findClass("com.itangcent.spring.entity.EntityController")
        assertNotNull("Should find EntityController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        
        val putEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.PUT }
        assertNotNull("Should find PUT endpoint", putEndpoint)
        
        val responseBody = putEndpoint!!.httpMetadata?.responseBody
        assertNotNull("Response body should not be null", responseBody)
    }

    fun testRequestEntityIsUnwrapped() = runTest {
        val psiClass = findClass("com.itangcent.spring.entity.EntityController")
        assertNotNull("Should find EntityController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        
        val postEndpoint = endpoints.find { it.httpMetadata?.path == "/entity/request" }
        assertNotNull("Should find /entity/request endpoint", postEndpoint)
        
        val body = postEndpoint!!.httpMetadata?.body
        assertNotNull("Request body should not be null", body)
    }
}
