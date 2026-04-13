package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class JavaxValidationConfigIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

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
        loadFile("javax/validation/constraints/NotNull.java")
        loadFile("javax/validation/constraints/NotBlank.java")
        loadFile("javax/validation/constraints/NotEmpty.java")
        loadFile("api/validation/javax/JavaxValidatedUserDTO.java")
        loadFile("api/validation/javax/JavaxValidationController.java")
    }

    override fun createConfigReader(): TestConfigReader {
        val extension = ExtensionConfigRegistry.getExtension("javax-validation")
        assertNotNull("javax-validation extension should exist", extension)
        val content = extension?.content ?: ""
        assertTrue("Extension content should not be blank", content.isNotBlank())
        return TestConfigReader.fromConfigText(content)
    }


    fun testJavaxValidationConfigLoadsCorrectly() = runTest {
        val extension = ExtensionConfigRegistry.getExtension("javax-validation")
        assertNotNull("javax-validation extension should exist", extension)
        assertEquals("Extension code should be javax-validation", "javax-validation", extension?.code)
        assertTrue("Extension should have content", extension?.content?.isNotBlank() == true)
        
        val configReader = createConfigReader()
        assertTrue("param.required rules should exist", configReader.getAll("param.required").isNotEmpty())
        assertTrue("field.required rules should exist", configReader.getAll("field.required").isNotEmpty())
    }

    fun testJavaxValidationControllerExportsEndpoints() = runTest {
        val psiClass = findClass("com.itangcent.validation.javax.JavaxValidationController")
        assertNotNull("Should find JavaxValidationController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertEquals("Should export 2 endpoints", 2, endpoints.size)

        val postEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.POST }
        assertNotNull("Should find POST endpoint", postEndpoint)
        assertEquals("POST endpoint path should be /javax/validated/user", "/javax/validated/user", postEndpoint?.httpMetadata?.path)

        val getEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull("Should find GET endpoint", getEndpoint)
        assertTrue("GET endpoint path should contain /javax/validated/user", getEndpoint?.httpMetadata?.path?.contains("/javax/validated/user") == true)
    }
}
