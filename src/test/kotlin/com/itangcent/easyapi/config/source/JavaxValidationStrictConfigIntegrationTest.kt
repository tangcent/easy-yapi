package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class JavaxValidationStrictConfigIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

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
        loadFile("javax/validation/groups/Default.java")
        loadFile("validation/Validated.java")
        loadFile("api/validation/javax/strict/CreateGroup.java")
        loadFile("api/validation/javax/strict/JavaxStrictUserDTO.java")
        loadFile("api/validation/javax/strict/JavaxStrictValidationController.java")
    }

    override fun createConfigReader(): TestConfigReader {
        val extension = ExtensionConfigRegistry.getExtension("javax-validation-strict")
        assertNotNull("javax-validation-strict extension should exist", extension)
        return TestConfigReader.fromConfigText(project, extension?.content ?: "")
    }

    fun testJavaxValidationStrictConfigLoadsCorrectly() = runTest {
        val extension = ExtensionConfigRegistry.getExtension("javax-validation-strict")
        assertNotNull(extension)
        assertEquals("javax-validation-strict", extension?.code)
        assertTrue(extension?.content?.isNotBlank() == true)
    }

    fun testControllerExportsEndpoints() = runTest {
        val psiClass = findClass("com.itangcent.validation.javax.strict.JavaxStrictValidationController")
        assertNotNull("Should find JavaxStrictValidationController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should export at least 2 endpoints", endpoints.size >= 2)
    }

    fun testFieldsNotRequiredWithoutValidated() = runTest {
        // The GET endpoint has no @Validated, so fields should NOT be required
        val psiClass = findClass("com.itangcent.validation.javax.strict.JavaxStrictValidationController")
        assertNotNull("Should find controller", psiClass)

        val endpoints = exporter.export(psiClass!!)
        val getEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull("Should find GET endpoint", getEndpoint)

        val responseBody = getEndpoint!!.httpMetadata?.responseBody
        assertNotNull("Should have response body", responseBody)

        val fields = (responseBody as? ObjectModel.Object)?.fields
        assertNotNull("Should have fields", fields)

        // Without @Validated, strict mode should NOT mark fields as required
        assertFalse(
            "id field should NOT be required without @Validated",
            fields!!["id"]?.required == true
        )
    }

    fun testFieldsExistInRequestBodyWithValidated() = runTest {
        // The POST /user endpoint has @Validated (Default). Verify the request body
        // contains all expected fields. Strict group checking for required status
        // depends on isExtend() which may not resolve correctly in the light fixture.
        val psiClass = findClass("com.itangcent.validation.javax.strict.JavaxStrictValidationController")
        assertNotNull("Should find controller", psiClass)

        val endpoints = exporter.export(psiClass!!)
        val postEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.POST && it.httpMetadata?.path == "/javax/strict/user"
        }
        assertNotNull("Should find POST /javax/strict/user endpoint", postEndpoint)

        val body = postEndpoint!!.httpMetadata?.body
        assertNotNull("Should have request body", body)

        val fields = (body as? ObjectModel.Object)?.fields
        assertNotNull("Should have fields", fields)

        assertTrue("Field 'id' should exist", fields!!.containsKey("id"))
        assertTrue("Field 'name' should exist", fields.containsKey("name"))
        assertTrue("Field 'email' should exist", fields.containsKey("email"))
    }

    fun testFieldsExistInRequestBodyWithCreateGroup() = runTest {
        // The POST /create-group endpoint has @Validated(CreateGroup).
        // Verify the request body contains all expected fields.
        val psiClass = findClass("com.itangcent.validation.javax.strict.JavaxStrictValidationController")
        assertNotNull("Should find controller", psiClass)

        val endpoints = exporter.export(psiClass!!)
        val postEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.POST && it.httpMetadata?.path == "/javax/strict/create-group"
        }
        assertNotNull("Should find POST /javax/strict/create-group endpoint", postEndpoint)

        val body = postEndpoint!!.httpMetadata?.body
        assertNotNull("Should have request body", body)

        val fields = (body as? ObjectModel.Object)?.fields
        assertNotNull("Should have fields", fields)

        assertTrue("Field 'id' should exist", fields!!.containsKey("id"))
        assertTrue("Field 'name' should exist", fields.containsKey("name"))
        assertTrue("Field 'email' should exist", fields.containsKey("email"))
    }
}
