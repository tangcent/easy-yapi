package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class JakartaValidationConfigIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

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
        loadFile("jakarta/validation/constraints/NotNull.java")
        loadFile("jakarta/validation/constraints/NotBlank.java")
        loadFile("jakarta/validation/constraints/NotEmpty.java")
        loadFile("api/validation/ValidatedUserDTO.java")
        loadFile("api/validation/ValidationController.java")
    }

    override fun createConfigReader(): TestConfigReader {
        val extension = ExtensionConfigRegistry.getExtension("jakarta-validation")
        assertNotNull("jakarta-validation extension should exist", extension)
        val content = extension?.content ?: ""
        assertTrue("Extension content should not be blank", content.isNotBlank())
        return TestConfigReader.fromConfigText(project, content)
    }


    fun testJakartaValidationConfigLoadsCorrectly() = runTest {
        val extension = ExtensionConfigRegistry.getExtension("jakarta-validation")
        assertNotNull("jakarta-validation extension should exist", extension)
        assertEquals("Extension code should be jakarta-validation", "jakarta-validation", extension?.code)
        assertTrue("Extension should have content", extension?.content?.isNotBlank() == true)
        
        val configReader = createConfigReader()
        assertTrue("param.required rules should exist", configReader.getAll("param.required").isNotEmpty())
        assertTrue("field.required rules should exist", configReader.getAll("field.required").isNotEmpty())
    }

    fun testJakartaValidationControllerExportsEndpoints() = runTest {
        val psiClass = findClass("com.itangcent.validation.ValidationController")
        assertNotNull("Should find ValidationController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertEquals("Should export 2 endpoints", 2, endpoints.size)

        val postEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.POST }
        assertNotNull("Should find POST endpoint", postEndpoint)
        assertEquals("POST endpoint path should be /validated/user", "/validated/user", postEndpoint?.httpMetadata?.path)

        val getEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull("Should find GET endpoint", getEndpoint)
        assertTrue("GET endpoint path should contain /validated/user", getEndpoint?.httpMetadata?.path?.contains("/validated/user") == true)
    }
}
