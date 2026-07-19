package com.itangcent.easyapi.core.config.source

import com.itangcent.easyapi.core.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.core.psi.PsiClassHelper
import com.itangcent.easyapi.core.rule.RuleKeys
import com.itangcent.easyapi.core.rule.engine.RuleEngine
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Integration test for the `field-utils` extension.
 *
 * The extension defines rules for:
 *   - [RuleKeys.FIELD_IGNORE]: ignore transient fields, fields from `java.lang` system
 *     classes, non-private/protected fields, and fields of common system types
 *     (`Class`, `ClassLoader`, `Thread`, etc.)
 *   - [RuleKeys.CONSTANT_FIELD_IGNORE]: ignore `serialVersionUID`
 *   - `ignore_static_and_final_field=false`: keep `static final` fields
 */
class FieldUtilsConfigIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
        loadTestFiles()
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
        loadFile("api/fieldutils/FieldUtilsDTO.java")
    }

    override fun createConfigReader(): TestConfigReader {
        val extension = ExtensionConfigRegistry.getExtension("field-utils")
        assertNotNull("field-utils extension should exist", extension)
        return TestConfigReader.fromConfigText(project, extension?.content ?: "")
    }

    fun testFieldUtilsConfigLoadsCorrectly() = runTest {
        val extension = ExtensionConfigRegistry.getExtension("field-utils")
        assertNotNull(extension)
        assertEquals("field-utils", extension?.code)
        assertTrue(extension?.content?.isNotBlank() == true)
    }

    /**
     * The core rule: a `transient` field should resolve [RuleKeys.FIELD_IGNORE]
     * to `true`.
     */
    fun testFieldIgnoreRuleForTransientField() = runTest {
        val psiClass = findClass("com.itangcent.fieldutils.FieldUtilsDTO")
        assertNotNull("Should find FieldUtilsDTO", psiClass)

        val transientField = psiClass!!.fields.find { it.name == "transientField" }
        assertNotNull("Should find transientField", transientField)

        val ruleEngine = RuleEngine.getInstance(project)
        val ignored = ruleEngine.evaluate(RuleKeys.FIELD_IGNORE, transientField!!)
        assertTrue(
            "FIELD_IGNORE should be true for transient field",
            ignored
        )
    }

    /**
     * The core rule: a public field (not private/protected) should resolve
     * [RuleKeys.FIELD_IGNORE] to `true`.
     */
    fun testFieldIgnoreRuleForPublicField() = runTest {
        val psiClass = findClass("com.itangcent.fieldutils.FieldUtilsDTO")
        assertNotNull("Should find FieldUtilsDTO", psiClass)

        val publicField = psiClass!!.fields.find { it.name == "publicField" }
        assertNotNull("Should find publicField", publicField)

        val ruleEngine = RuleEngine.getInstance(project)
        val ignored = ruleEngine.evaluate(RuleKeys.FIELD_IGNORE, publicField!!)
        assertTrue(
            "FIELD_IGNORE should be true for public field (not private/protected)",
            ignored
        )
    }

    /**
     * The core rule: a normal private field should resolve [RuleKeys.FIELD_IGNORE]
     * to `false`.
     */
    fun testFieldIgnoreRuleForPrivateField() = runTest {
        val psiClass = findClass("com.itangcent.fieldutils.FieldUtilsDTO")
        assertNotNull("Should find FieldUtilsDTO", psiClass)

        val normalField = psiClass!!.fields.find { it.name == "normalField" }
        assertNotNull("Should find normalField", normalField)

        val ruleEngine = RuleEngine.getInstance(project)
        val ignored = ruleEngine.evaluate(RuleKeys.FIELD_IGNORE, normalField!!)
        assertFalse(
            "FIELD_IGNORE should be false for private non-transient field",
            ignored
        )
    }

    /**
     * The core rule: a `serialVersionUID` field should resolve
     * [RuleKeys.CONSTANT_FIELD_IGNORE] to `true`.
     */
    fun testConstantFieldIgnoreRuleForSerialVersionUID() = runTest {
        val psiClass = findClass("com.itangcent.fieldutils.FieldUtilsDTO")
        assertNotNull("Should find FieldUtilsDTO", psiClass)

        val serialVersionUIDField = psiClass!!.fields.find { it.name == "serialVersionUID" }
        assertNotNull("Should find serialVersionUID field", serialVersionUIDField)

        val ruleEngine = RuleEngine.getInstance(project)
        val ignored = ruleEngine.evaluate(RuleKeys.CONSTANT_FIELD_IGNORE, serialVersionUIDField!!)
        assertTrue(
            "CONSTANT_FIELD_IGNORE should be true for serialVersionUID",
            ignored
        )
    }

    /**
     * The core rule: a non-`serialVersionUID` constant field should resolve
     * [RuleKeys.CONSTANT_FIELD_IGNORE] to `false`.
     */
    fun testConstantFieldIgnoreRuleForOtherConstantField() = runTest {
        val psiClass = findClass("com.itangcent.fieldutils.FieldUtilsDTO")
        assertNotNull("Should find FieldUtilsDTO", psiClass)

        val normalField = psiClass!!.fields.find { it.name == "normalField" }
        assertNotNull("Should find normalField", normalField)

        val ruleEngine = RuleEngine.getInstance(project)
        val ignored = ruleEngine.evaluate(RuleKeys.CONSTANT_FIELD_IGNORE, normalField!!)
        assertFalse(
            "CONSTANT_FIELD_IGNORE should be false for non-serialVersionUID field",
            ignored
        )
    }

    // ── Exporter-level tests (end-to-end) ────────────────────────

    fun testTransientFieldIsIgnored() = runTest {
        val psiClass = findClass("com.itangcent.fieldutils.FieldUtilsDTO")
        assertNotNull("Should find FieldUtilsDTO", psiClass)

        val helper = PsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass!!)
        assertNotNull("Should build object model", model)

        val fields = model?.asObject()?.fields
        assertNotNull("Should have fields", fields)

        assertFalse("Transient field 'transientField' should be ignored", fields!!.containsKey("transientField"))
    }

    fun testSerialVersionUIDIsIgnored() = runTest {
        val psiClass = findClass("com.itangcent.fieldutils.FieldUtilsDTO")
        assertNotNull("Should find FieldUtilsDTO", psiClass)

        val helper = PsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass!!)
        assertNotNull("Should build object model", model)

        val fields = model?.asObject()?.fields
        assertNotNull("Should have fields", fields)

        assertFalse("serialVersionUID should be ignored", fields!!.containsKey("serialVersionUID"))
    }

    fun testNormalFieldIsKept() = runTest {
        val psiClass = findClass("com.itangcent.fieldutils.FieldUtilsDTO")
        assertNotNull("Should find FieldUtilsDTO", psiClass)

        val helper = PsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass!!)
        assertNotNull("Should build object model", model)

        val fields = model?.asObject()?.fields
        assertNotNull("Should have fields", fields)

        assertTrue("Normal private field 'normalField' should be kept", fields!!.containsKey("normalField"))
    }

    fun testPublicFieldIsIgnored() = runTest {
        val psiClass = findClass("com.itangcent.fieldutils.FieldUtilsDTO")
        assertNotNull("Should find FieldUtilsDTO", psiClass)

        val helper = PsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass!!)
        assertNotNull("Should build object model", model)

        val fields = model?.asObject()?.fields
        assertNotNull("Should have fields", fields)

        // field-utils config ignores fields that are not private or protected
        assertFalse("Public field 'publicField' should be ignored", fields!!.containsKey("publicField"))
    }
}
