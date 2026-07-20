package com.itangcent.easyapi.framework.springmvc.config

import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.framework.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.core.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.core.rule.RuleKeys
import com.itangcent.easyapi.core.rule.engine.RuleEngine
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Integration test for the `jakarta-validation` extension.
 *
 * The extension defines rules for [RuleKeys.FIELD_REQUIRED] and [RuleKeys.PARAM_REQUIRED]:
 *   - `field.required=@jakarta.validation.constraints.NotBlank` (and NotNull, NotEmpty)
 *   - `param.required=@jakarta.validation.constraints.NotBlank` (and NotNull, NotEmpty)
 *
 * With the AnnotationExpressionParser fix, a boolean rule like
 * `field.required=@jakarta.validation.constraints.NotNull` resolves to `true` when the
 * annotation is present (no attribute specified → presence check).
 */
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
    }

    /**
     * The core rule: a field annotated with `@NotNull` should resolve
     * [RuleKeys.FIELD_REQUIRED] to `true`.
     */
    fun testFieldRequiredRuleForNotNullField() = runTest {
        val psiClass = findClass("com.itangcent.validation.ValidatedUserDTO")
        assertNotNull("Should find ValidatedUserDTO", psiClass)

        val idField = psiClass!!.fields.find { it.name == "id" }
        assertNotNull("Should find id field", idField)

        val ruleEngine = RuleEngine.getInstance(project)
        val required = ruleEngine.evaluate(RuleKeys.FIELD_REQUIRED, idField!!)
        assertTrue(
            "FIELD_REQUIRED should be true for field with @NotNull",
            required
        )
    }

    /**
     * The core rule: a field annotated with `@NotBlank` should resolve
     * [RuleKeys.FIELD_REQUIRED] to `true`.
     */
    fun testFieldRequiredRuleForNotBlankField() = runTest {
        val psiClass = findClass("com.itangcent.validation.ValidatedUserDTO")
        assertNotNull("Should find ValidatedUserDTO", psiClass)

        val nameField = psiClass!!.fields.find { it.name == "name" }
        assertNotNull("Should find name field", nameField)

        val ruleEngine = RuleEngine.getInstance(project)
        val required = ruleEngine.evaluate(RuleKeys.FIELD_REQUIRED, nameField!!)
        assertTrue(
            "FIELD_REQUIRED should be true for field with @NotBlank",
            required
        )
    }

    /**
     * The core rule: a field annotated with `@NotEmpty` should resolve
     * [RuleKeys.FIELD_REQUIRED] to `true`.
     */
    fun testFieldRequiredRuleForNotEmptyField() = runTest {
        val psiClass = findClass("com.itangcent.validation.ValidatedUserDTO")
        assertNotNull("Should find ValidatedUserDTO", psiClass)

        val emailField = psiClass!!.fields.find { it.name == "email" }
        assertNotNull("Should find email field", emailField)

        val ruleEngine = RuleEngine.getInstance(project)
        val required = ruleEngine.evaluate(RuleKeys.FIELD_REQUIRED, emailField!!)
        assertTrue(
            "FIELD_REQUIRED should be true for field with @NotEmpty",
            required
        )
    }

    /**
     * The core rule: a field without any validation annotation should resolve
     * [RuleKeys.FIELD_REQUIRED] to `false`.
     */
    fun testFieldRequiredRuleForUnannotatedField() = runTest {
        val psiClass = findClass("com.itangcent.validation.ValidatedUserDTO")
        assertNotNull("Should find ValidatedUserDTO", psiClass)

        val addressField = psiClass!!.fields.find { it.name == "address" }
        assertNotNull("Should find address field", addressField)

        val ruleEngine = RuleEngine.getInstance(project)
        val required = ruleEngine.evaluate(RuleKeys.FIELD_REQUIRED, addressField!!)
        assertFalse(
            "FIELD_REQUIRED should be false for field without validation annotations",
            required
        )
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
