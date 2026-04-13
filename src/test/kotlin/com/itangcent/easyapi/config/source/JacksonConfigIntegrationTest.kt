package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class JacksonConfigIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

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
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/RestController.java")
        loadFile("spring/Controller.java")
        loadFile("model/Result.java")
        loadFile("model/IResult.java")
        loadFile("model/UserInfo.java")
        loadFile("com/fasterxml/jackson/annotation/JsonProperty.java")
        loadFile("com/fasterxml/jackson/annotation/JsonIgnore.java")
        loadFile("com/fasterxml/jackson/annotation/JsonFormat.java")
        loadFile("api/jackson/UserDTO.java")
        loadFile("api/jackson/UserController.java")
    }

    override fun createConfigReader(): TestConfigReader {
        val extension = ExtensionConfigRegistry.getExtension("jackson")
        assertNotNull("jackson extension should exist", extension)
        return TestConfigReader.fromConfigText(extension?.content ?: "")
    }


    fun testJacksonConfigLoadsCorrectly() = runTest {
        val extension = ExtensionConfigRegistry.getExtension("jackson")
        assertNotNull("jackson extension should exist", extension)
        assertEquals("Extension code should be jackson", "jackson", extension?.code)
        assertTrue("Extension should have content", extension?.content?.isNotBlank() == true)
    }

    fun testUserControllerExportsEndpoints() = runTest {
        val psiClass = findClass("com.itangcent.jackson.UserController")
        assertNotNull("Should find UserController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertEquals("Should export 2 endpoints", 2, endpoints.size)

        val postEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.POST }
        assertNotNull("Should find POST endpoint", postEndpoint)
        assertEquals("POST endpoint path should be /user/create", "/user/create", postEndpoint?.httpMetadata?.path)

        val getEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull("Should find GET endpoint", getEndpoint)
        assertTrue("GET endpoint path should contain /user/get", getEndpoint?.httpMetadata?.path?.contains("/user/get") == true)
    }
}
