package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.psi.PsiClassHelper
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Integration test for the `fastjson` extension.
 *
 * The extension defines a single rule for [RuleKeys.FIELD_NAME]:
 *   `field.name=@com.alibaba.fastjson.annotation.JSONField#value`
 * which renames a field to the value of `@JSONField(value = "...")`.
 */
class FastjsonConfigIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

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
        loadFile("com/alibaba/fastjson/annotation/JSONField.java")
        loadFile("api/fastjson/FastjsonDTO.java")
        loadFile("api/fastjson/FastjsonController.java")
    }

    override fun createConfigReader(): TestConfigReader {
        val extension = ExtensionConfigRegistry.getExtension("fastjson")
        assertNotNull("fastjson extension should exist", extension)
        return TestConfigReader.fromConfigText(project, extension?.content ?: "")
    }

    fun testFastjsonConfigLoadsCorrectly() = runTest {
        val extension = ExtensionConfigRegistry.getExtension("fastjson")
        assertNotNull("fastjson extension should exist", extension)
        assertEquals("fastjson", extension?.code)
        assertTrue(extension?.content?.isNotBlank() == true)
    }

    /**
     * The core rule: a field annotated with `@JSONField(value = "product_id")`
     * should resolve [RuleKeys.FIELD_NAME] to `"product_id"`.
     */
    fun testFieldNameRuleForAnnotatedField() = runTest {
        val psiClass = findClass("com.itangcent.fastjson.FastjsonDTO")
        assertNotNull("Should find FastjsonDTO", psiClass)

        val idField = psiClass!!.fields.find { it.name == "id" }
        assertNotNull("Should find id field", idField)

        val ruleEngine = RuleEngine.getInstance(project)
        val name = ruleEngine.evaluate(RuleKeys.FIELD_NAME, idField!!)
        assertEquals(
            "FIELD_NAME should be 'product_id' for field with @JSONField(value = \"product_id\")",
            "product_id",
            name
        )
    }

    /**
     * The core rule: a field annotated with `@JSONField(value = "product_name")`
     * should resolve [RuleKeys.FIELD_NAME] to `"product_name"`.
     */
    fun testFieldNameRuleForNameField() = runTest {
        val psiClass = findClass("com.itangcent.fastjson.FastjsonDTO")
        assertNotNull("Should find FastjsonDTO", psiClass)

        val nameField = psiClass!!.fields.find { it.name == "name" }
        assertNotNull("Should find name field", nameField)

        val ruleEngine = RuleEngine.getInstance(project)
        val name = ruleEngine.evaluate(RuleKeys.FIELD_NAME, nameField!!)
        assertEquals(
            "FIELD_NAME should be 'product_name' for field with @JSONField(value = \"product_name\")",
            "product_name",
            name
        )
    }

    /**
     * The core rule: a field without `@JSONField` should resolve
     * [RuleKeys.FIELD_NAME] to `null` (no rename applies).
     */
    fun testFieldNameRuleForUnannotatedField() = runTest {
        val psiClass = findClass("com.itangcent.fastjson.FastjsonDTO")
        assertNotNull("Should find FastjsonDTO", psiClass)

        val descriptionField = psiClass!!.fields.find { it.name == "description" }
        assertNotNull("Should find description field", descriptionField)

        val ruleEngine = RuleEngine.getInstance(project)
        val name = ruleEngine.evaluate(RuleKeys.FIELD_NAME, descriptionField!!)
        assertNull(
            "FIELD_NAME should be null for field without @JSONField",
            name
        )
    }

    fun testJSONFieldRenamesFields() = runTest {
        val psiClass = findClass("com.itangcent.fastjson.FastjsonDTO")
        assertNotNull("Should find FastjsonDTO", psiClass)

        val helper = PsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass!!)
        assertNotNull("Should build object model", model)

        val fields = model?.asObject()?.fields
        assertNotNull("Should have fields", fields)

        assertTrue("Field 'id' should be renamed to 'product_id' via @JSONField", fields!!.containsKey("product_id"))
        assertTrue("Field 'name' should be renamed to 'product_name' via @JSONField", fields.containsKey("product_name"))
        assertFalse("Original field 'id' should NOT exist", fields.containsKey("id"))
        assertFalse("Original field 'name' should NOT exist", fields.containsKey("name"))
    }

    fun testFieldWithoutJSONFieldKeepsOriginalName() = runTest {
        val psiClass = findClass("com.itangcent.fastjson.FastjsonDTO")
        assertNotNull("Should find FastjsonDTO", psiClass)

        val helper = PsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass!!)
        assertNotNull("Should build object model", model)

        val fields = model?.asObject()?.fields
        assertNotNull("Should have fields", fields)

        assertTrue("Field 'description' (without @JSONField) should exist", fields!!.containsKey("description"))
    }

    fun testControllerExportsEndpoints() = runTest {
        val psiClass = findClass("com.itangcent.fastjson.FastjsonController")
        assertNotNull("Should find FastjsonController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertEquals("Should export 1 endpoint", 1, endpoints.size)

        val postEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.POST }
        assertNotNull("Should find POST endpoint", postEndpoint)
        assertEquals("/fastjson/create", postEndpoint?.httpMetadata?.path)
    }
}
