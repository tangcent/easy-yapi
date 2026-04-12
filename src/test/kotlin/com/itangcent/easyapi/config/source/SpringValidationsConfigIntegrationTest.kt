package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class SpringValidationsConfigIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporter: SpringMvcClassExporter

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        exporter = SpringMvcClassExporter(actionContext)
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
        loadFile("org/springframework/lang/NonNull.java")
        loadFile("org/springframework/validation/BindingResult.java")
        loadFile("org/springframework/format/annotation/DateTimeFormat.java")
        loadFile("api/validation/spring/SpringValidatedUserDTO.java")
        loadFile("api/validation/spring/SpringValidationController.java")
    }

    override fun createConfigReader(): TestConfigReader {
        val extension = ExtensionConfigRegistry.getExtension("spring-validations")
        assertNotNull("spring-validations extension should exist", extension)
        val content = extension?.content ?: ""
        assertTrue("Extension content should not be blank", content.isNotBlank())
        return TestConfigReader.fromConfigText(content)
    }

    override fun customizeContext(builder: com.itangcent.easyapi.core.context.ActionContextBuilder) {
        builder.bind(com.itangcent.easyapi.psi.helper.DocHelper::class, com.itangcent.easyapi.psi.helper.StandardDocHelper())
    }

    fun testSpringValidationsConfigLoadsCorrectly() = runTest {
        val extension = ExtensionConfigRegistry.getExtension("spring-validations")
        assertNotNull("spring-validations extension should exist", extension)
        assertEquals("Extension code should be spring-validations", "spring-validations", extension?.code)
        assertTrue("Extension should have content", extension?.content?.isNotBlank() == true)
        
        val configReader = createConfigReader()
        assertTrue("field.required rules should exist", configReader.getAll("field.required").isNotEmpty())
        assertTrue("param.ignore rules should exist", configReader.getAll("param.ignore").isNotEmpty())
    }

    fun testSpringValidationControllerExportsEndpoints() = runTest {
        val psiClass = findClass("com.itangcent.validation.spring.SpringValidationController")
        assertNotNull("Should find SpringValidationController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertEquals("Should export 2 endpoints", 2, endpoints.size)

        val postEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.POST }
        assertNotNull("Should find POST endpoint", postEndpoint)
        assertEquals("POST endpoint path should be /spring/validated/user", "/spring/validated/user", postEndpoint?.httpMetadata?.path)

        val getEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull("Should find GET endpoint", getEndpoint)
        assertTrue("GET endpoint path should contain /spring/validated/user", getEndpoint?.httpMetadata?.path?.contains("/spring/validated/user") == true)
    }

    fun testBindingResultIsIgnored() = runTest {
        val psiClass = findClass("com.itangcent.validation.spring.SpringValidationController")
        assertNotNull("Should find SpringValidationController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        val postEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.POST }
        assertNotNull("Should find POST endpoint", postEndpoint)
        
        val params = postEndpoint!!.httpMetadata?.parameters ?: emptyList()
        val bindingResultParams = params.filter { it.name.contains("bindingResult", ignoreCase = true) }
        assertEquals("BindingResult should be ignored", 0, bindingResultParams.size)
    }
}
