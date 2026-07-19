package com.itangcent.easyapi.framework.springmvc.config

import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.framework.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.core.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.core.psi.PsiClassHelper
import com.itangcent.easyapi.core.rule.RuleKeys
import com.itangcent.easyapi.core.rule.engine.RuleEngine
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Integration test for the `gson` extension.
 *
 * The extension defines two rules:
 *   - [RuleKeys.FIELD_NAME]: `field.name=@com.google.gson.annotations.SerializedName#value`
 *     renames a field to the value of `@SerializedName(value = "...")`.
 *   - [RuleKeys.FIELD_IGNORE]: `field.ignore=!@com.google.gson.annotations.Expose#serialize`
 *     ignores a field when `@Expose(serialize = false)`.
 */
class GsonConfigIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

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
        loadFile("com/google/gson/annotations/SerializedName.java")
        loadFile("com/google/gson/annotations/Expose.java")
        loadFile("api/gson/ProductDTO.java")
        loadFile("api/gson/ProductController.java")
    }

    override fun createConfigReader(): TestConfigReader {
        val extension = ExtensionConfigRegistry.getExtension("gson")
        assertNotNull("gson extension should exist", extension)
        return TestConfigReader.fromConfigText(project, extension?.content ?: "")
    }


    fun testGsonConfigLoadsCorrectly() = runTest {
        val extension = ExtensionConfigRegistry.getExtension("gson")
        assertNotNull("gson extension should exist", extension)
        assertEquals("Extension code should be gson", "gson", extension?.code)
        assertTrue("Extension should have content", extension?.content?.isNotBlank() == true)
    }

    /**
     * The core rule: a field annotated with `@SerializedName("product_id")`
     * should resolve [RuleKeys.FIELD_NAME] to `"product_id"`.
     */
    fun testFieldNameRuleForSerializedNameField() = runTest {
        val psiClass = findClass("com.itangcent.gson.ProductDTO")
        assertNotNull("Should find ProductDTO", psiClass)

        val idField = psiClass!!.fields.find { it.name == "id" }
        assertNotNull("Should find id field", idField)

        val ruleEngine = RuleEngine.getInstance(project)
        val name = ruleEngine.evaluate(RuleKeys.FIELD_NAME, idField!!)
        assertEquals(
            "FIELD_NAME should be 'product_id' for field with @SerializedName(\"product_id\")",
            "product_id",
            name
        )
    }

    /**
     * The core rule: a field annotated with `@SerializedName("product_name")`
     * should resolve [RuleKeys.FIELD_NAME] to `"product_name"`.
     */
    fun testFieldNameRuleForSerializedNameNameField() = runTest {
        val psiClass = findClass("com.itangcent.gson.ProductDTO")
        assertNotNull("Should find ProductDTO", psiClass)

        val nameField = psiClass!!.fields.find { it.name == "name" }
        assertNotNull("Should find name field", nameField)

        val ruleEngine = RuleEngine.getInstance(project)
        val name = ruleEngine.evaluate(RuleKeys.FIELD_NAME, nameField!!)
        assertEquals(
            "FIELD_NAME should be 'product_name' for field with @SerializedName(\"product_name\")",
            "product_name",
            name
        )
    }

    /**
     * The core rule: a field without `@SerializedName` should resolve
     * [RuleKeys.FIELD_NAME] to `null` (no rename applies).
     */
    fun testFieldNameRuleForUnannotatedField() = runTest {
        val psiClass = findClass("com.itangcent.gson.ProductDTO")
        assertNotNull("Should find ProductDTO", psiClass)

        val priceField = psiClass!!.fields.find { it.name == "price" }
        assertNotNull("Should find price field", priceField)

        val ruleEngine = RuleEngine.getInstance(project)
        val name = ruleEngine.evaluate(RuleKeys.FIELD_NAME, priceField!!)
        assertNull(
            "FIELD_NAME should be null for field without @SerializedName",
            name
        )
    }

    /**
     * The core rule: a field annotated with `@Expose(serialize = false)`
     * should resolve [RuleKeys.FIELD_IGNORE] to `true`.
     */
    fun testFieldIgnoreRuleForExposedFalseField() = runTest {
        val psiClass = findClass("com.itangcent.gson.ProductDTO")
        assertNotNull("Should find ProductDTO", psiClass)

        val internalCodeField = psiClass!!.fields.find { it.name == "internalCode" }
        assertNotNull("Should find internalCode field", internalCodeField)

        val ruleEngine = RuleEngine.getInstance(project)
        val ignored = ruleEngine.evaluate(RuleKeys.FIELD_IGNORE, internalCodeField!!)
        assertTrue(
            "FIELD_IGNORE should be true for field with @Expose(serialize = false)",
            ignored
        )
    }

    /**
     * The core rule: a field without `@Expose(serialize = false)` should resolve
     * [RuleKeys.FIELD_IGNORE] to `false`.
     */
    fun testFieldIgnoreRuleForNonExposedField() = runTest {
        val psiClass = findClass("com.itangcent.gson.ProductDTO")
        assertNotNull("Should find ProductDTO", psiClass)

        val priceField = psiClass!!.fields.find { it.name == "price" }
        assertNotNull("Should find price field", priceField)

        val ruleEngine = RuleEngine.getInstance(project)
        val ignored = ruleEngine.evaluate(RuleKeys.FIELD_IGNORE, priceField!!)
        assertFalse(
            "FIELD_IGNORE should be false for field without @Expose(serialize = false)",
            ignored
        )
    }

    fun testProductControllerExportsEndpoints() = runTest {
        val psiClass = findClass("com.itangcent.gson.ProductController")
        assertNotNull("Should find ProductController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertEquals("Should export 2 endpoints", 2, endpoints.size)

        val postEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.POST }
        assertNotNull("Should find POST endpoint", postEndpoint)
        assertEquals("POST endpoint path should be /product/create", "/product/create", postEndpoint?.httpMetadata?.path)

        val getEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull("Should find GET endpoint", getEndpoint)
        assertTrue("GET endpoint path should contain /product/get", getEndpoint?.httpMetadata?.path?.contains("/product/get") == true)
    }

    fun testFieldsWithSerializedNameAreRenamed() = runTest {
        val psiClass = findClass("com.itangcent.gson.ProductDTO")
        assertNotNull("Should find ProductDTO", psiClass)

        val helper = PsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass!!)
        assertNotNull("Should build object model", model)

        val objectData = model?.asObject()
        assertNotNull("Should be an object", objectData)

        val fields = objectData!!.fields
        assertTrue("Field 'id' should be renamed to 'product_id' via @SerializedName", fields.containsKey("product_id"))
        assertTrue("Field 'name' should be renamed to 'product_name' via @SerializedName", fields.containsKey("product_name"))
        assertTrue("Field 'price' (without any annotation) should exist", fields.containsKey("price"))
    }
}
