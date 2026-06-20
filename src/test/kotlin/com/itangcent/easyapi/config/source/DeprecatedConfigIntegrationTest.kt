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
 * Integration test for the `deprecated` extension.
 *
 * The extension defines rules for [RuleKeys.METHOD_DOC] and [RuleKeys.FIELD_DOC]
 * that append a "「已废弃」" marker when an element is annotated with
 * `@java.lang.Deprecated` / `@kotlin.Deprecated` or carries a `@deprecated` doc tag.
 */
class DeprecatedConfigIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

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
        loadFile("api/deprecated/DeprecatedController.java")
        loadFile("api/deprecated/DeprecatedDTO.java")
    }

    override fun createConfigReader(): TestConfigReader {
        val extension = ExtensionConfigRegistry.getExtension("deprecated")
        assertNotNull("deprecated extension should exist", extension)
        return TestConfigReader.fromConfigText(project, extension?.content ?: "")
    }

    fun testDeprecatedConfigLoadsCorrectly() = runTest {
        val extension = ExtensionConfigRegistry.getExtension("deprecated")
        assertNotNull("deprecated extension should exist", extension)
        assertEquals("deprecated", extension?.code)
        assertTrue(extension?.content?.isNotBlank() == true)
    }

    /**
     * The core rule: a method annotated with `@java.lang.Deprecated` should resolve
     * [RuleKeys.METHOD_DOC] to a string containing "已废弃".
     */
    fun testMethodDocRuleForDeprecatedMethod() = runTest {
        val psiClass = findClass("com.itangcent.deprecated.DeprecatedController")
        assertNotNull("Should find DeprecatedController", psiClass)

        val oldMethod = psiClass!!.methods.find { it.name == "oldMethod" }
        assertNotNull("Should find oldMethod", oldMethod)

        val ruleEngine = RuleEngine.getInstance(project)
        val doc = ruleEngine.evaluate(RuleKeys.METHOD_DOC, oldMethod!!)
        assertNotNull("METHOD_DOC should not be null for deprecated method", doc)
        assertTrue(
            "Deprecated method doc should contain '已废弃'. Was: $doc",
            doc!!.contains("已废弃")
        )
    }

    /**
     * The core rule: a method without `@Deprecated` should NOT resolve
     * [RuleKeys.METHOD_DOC] to a deprecated marker.
     */
    fun testMethodDocRuleForNonDeprecatedMethod() = runTest {
        val psiClass = findClass("com.itangcent.deprecated.DeprecatedController")
        assertNotNull("Should find DeprecatedController", psiClass)

        val newMethod = psiClass!!.methods.find { it.name == "newMethod" }
        assertNotNull("Should find newMethod", newMethod)

        val ruleEngine = RuleEngine.getInstance(project)
        val doc = ruleEngine.evaluate(RuleKeys.METHOD_DOC, newMethod!!)
        assertTrue(
            "Non-deprecated method doc should NOT contain '已废弃'. Was: $doc",
            doc == null || !doc.contains("已废弃")
        )
    }

    /**
     * The core rule: a field annotated with `@java.lang.Deprecated` should resolve
     * [RuleKeys.FIELD_DOC] to a string containing "已废弃".
     */
    fun testFieldDocRuleForDeprecatedField() = runTest {
        val psiClass = findClass("com.itangcent.deprecated.DeprecatedDTO")
        assertNotNull("Should find DeprecatedDTO", psiClass)

        val oldField = psiClass!!.fields.find { it.name == "oldField" }
        assertNotNull("Should find oldField", oldField)

        val ruleEngine = RuleEngine.getInstance(project)
        val doc = ruleEngine.evaluate(RuleKeys.FIELD_DOC, oldField!!)
        assertNotNull("FIELD_DOC should not be null for deprecated field", doc)
        assertTrue(
            "Deprecated field doc should contain '已废弃'. Was: $doc",
            doc!!.contains("已废弃")
        )
    }

    /**
     * The core rule: a field without `@Deprecated` should NOT resolve
     * [RuleKeys.FIELD_DOC] to a deprecated marker.
     */
    fun testFieldDocRuleForNonDeprecatedField() = runTest {
        val psiClass = findClass("com.itangcent.deprecated.DeprecatedDTO")
        assertNotNull("Should find DeprecatedDTO", psiClass)

        val activeField = psiClass!!.fields.find { it.name == "activeField" }
        assertNotNull("Should find activeField", activeField)

        val ruleEngine = RuleEngine.getInstance(project)
        val doc = ruleEngine.evaluate(RuleKeys.FIELD_DOC, activeField!!)
        assertTrue(
            "Non-deprecated field doc should NOT contain '已废弃'. Was: $doc",
            doc == null || !doc.contains("已废弃")
        )
    }

    fun testDeprecatedMethodHasDeprecatedDoc() = runTest {
        val psiClass = findClass("com.itangcent.deprecated.DeprecatedController")
        assertNotNull("Should find DeprecatedController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        val oldEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.GET && it.httpMetadata?.path?.contains("old") == true
        }
        assertNotNull("Should find GET /deprecated/old endpoint", oldEndpoint)
        assertTrue(
            "Deprecated method should have deprecated doc. Description was: ${oldEndpoint?.description}",
            oldEndpoint?.description?.contains("已废弃") == true
        )
    }

    fun testNonDeprecatedMethodHasNoDeprecatedDoc() = runTest {
        val psiClass = findClass("com.itangcent.deprecated.DeprecatedController")
        assertNotNull("Should find DeprecatedController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        val newEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.GET && it.httpMetadata?.path?.contains("new") == true
        }
        assertNotNull("Should find GET /deprecated/new endpoint", newEndpoint)
        assertFalse(
            "Non-deprecated method should NOT have deprecated doc. Description was: ${newEndpoint?.description}",
            newEndpoint?.description?.contains("已废弃") == true
        )
    }

    fun testDeprecatedFieldHasDeprecatedDoc() = runTest {
        val psiClass = findClass("com.itangcent.deprecated.DeprecatedDTO")
        assertNotNull("Should find DeprecatedDTO", psiClass)

        val helper = PsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass!!)
        assertNotNull("Should build object model", model)

        val fields = model?.asObject()?.fields
        assertNotNull("Should have fields", fields)

        val oldField = fields!!["oldField"]
        assertNotNull("Field 'oldField' should exist", oldField)
        assertTrue(
            "Deprecated field should have deprecated doc. Comment was: ${oldField?.comment}",
            oldField?.comment?.contains("已废弃") == true
        )
    }

    fun testNonDeprecatedFieldHasNoDeprecatedDoc() = runTest {
        val psiClass = findClass("com.itangcent.deprecated.DeprecatedDTO")
        assertNotNull("Should find DeprecatedDTO", psiClass)

        val helper = PsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass!!)
        assertNotNull("Should build object model", model)

        val fields = model?.asObject()?.fields
        assertNotNull("Should have fields", fields)

        val activeField = fields!!["activeField"]
        assertNotNull("Field 'activeField' should exist", activeField)
        assertFalse(
            "Non-deprecated field should NOT have deprecated doc. Comment was: ${activeField?.comment}",
            activeField?.comment?.contains("已废弃") == true
        )
    }
}
