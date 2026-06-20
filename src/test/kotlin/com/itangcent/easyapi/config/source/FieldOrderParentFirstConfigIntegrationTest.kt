package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.psi.PsiClassHelper
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Integration test for the `field-order-parent-first` extension.
 *
 * The extension defines a rule for [RuleKeys.FIELD_ORDER_WITH]:
 *   - `field.order.with=groovy:...` (comparator-style)
 *
 * The comparator returns:
 *   - `0` if both fields are defined in the same class
 *   - `1` if `a`'s defining class extends `b`'s defining class (a is child → a after b → parent first)
 *   - `-1` otherwise (a before b)
 */
class FieldOrderParentFirstConfigIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

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
        loadFile("api/fieldorder/ParentDTO.java")
        loadFile("api/fieldorder/ChildDTO.java")
    }

    override fun createConfigReader(): TestConfigReader {
        val extension = ExtensionConfigRegistry.getExtension("field-order-parent-first")
        assertNotNull("field-order-parent-first extension should exist", extension)
        return TestConfigReader.fromConfigText(project, extension?.content ?: "")
    }

    fun testFieldOrderParentFirstConfigLoadsCorrectly() = runTest {
        val extension = ExtensionConfigRegistry.getExtension("field-order-parent-first")
        assertNotNull(extension)
        assertEquals("field-order-parent-first", extension?.code)
        assertTrue(extension?.content?.isNotBlank() == true)
    }

    /**
     * The core rule: comparing a parent field (a) against a child field (b)
     * should resolve [RuleKeys.FIELD_ORDER_WITH] to a negative value
     * (parent comes first).
     */
    fun testFieldOrderWithRuleParentBeforeChild() = runTest {
        val childClass = findClass("com.itangcent.fieldorder.ChildDTO")
        assertNotNull("Should find ChildDTO", childClass)
        val parentClass = findClass("com.itangcent.fieldorder.ParentDTO")
        assertNotNull("Should find ParentDTO", parentClass)

        val parentField = parentClass!!.fields.find { it.name == "parentField" }
        assertNotNull("Should find parentField", parentField)
        val childField = childClass!!.fields.find { it.name == "childField" }
        assertNotNull("Should find childField", childField)

        val ruleEngine = RuleEngine.getInstance(project)
        val result = ruleEngine.evaluate(RuleKeys.FIELD_ORDER_WITH, parentField!!) { ctx ->
            ctx.setExt("a", parentField)
            ctx.setExt("b", childField)
        }
        assertNotNull("FIELD_ORDER_WITH should not be null", result)
        val cmp = result!!.toIntOrNull()
        assertNotNull("FIELD_ORDER_WITH should resolve to an int. Was: $result", cmp)
        assertTrue(
            "Parent field should come before child field (cmp < 0). Was: $cmp",
            cmp!! < 0
        )
    }

    /**
     * The core rule: comparing a child field (a) against a parent field (b)
     * should resolve [RuleKeys.FIELD_ORDER_WITH] to a positive value
     * (child comes after parent).
     */
    fun testFieldOrderWithRuleChildAfterParent() = runTest {
        val childClass = findClass("com.itangcent.fieldorder.ChildDTO")
        assertNotNull("Should find ChildDTO", childClass)
        val parentClass = findClass("com.itangcent.fieldorder.ParentDTO")
        assertNotNull("Should find ParentDTO", parentClass)

        val parentField = parentClass!!.fields.find { it.name == "parentField" }
        assertNotNull("Should find parentField", parentField)
        val childField = childClass!!.fields.find { it.name == "childField" }
        assertNotNull("Should find childField", childField)

        val ruleEngine = RuleEngine.getInstance(project)
        val result = ruleEngine.evaluate(RuleKeys.FIELD_ORDER_WITH, childField!!) { ctx ->
            ctx.setExt("a", childField)
            ctx.setExt("b", parentField)
        }
        assertNotNull("FIELD_ORDER_WITH should not be null", result)
        val cmp = result!!.toIntOrNull()
        assertNotNull("FIELD_ORDER_WITH should resolve to an int. Was: $result", cmp)
        assertTrue(
            "Child field should come after parent field (cmp > 0). Was: $cmp",
            cmp!! > 0
        )
    }

    /**
     * The core rule: comparing two fields from the same class should resolve
     * [RuleKeys.FIELD_ORDER_WITH] to `0` (same order).
     */
    fun testFieldOrderWithRuleSameClassFields() = runTest {
        val parentClass = findClass("com.itangcent.fieldorder.ParentDTO")
        assertNotNull("Should find ParentDTO", parentClass)

        val parentField = parentClass!!.fields.find { it.name == "parentField" }
        assertNotNull("Should find parentField", parentField)

        val ruleEngine = RuleEngine.getInstance(project)
        val result = ruleEngine.evaluate(RuleKeys.FIELD_ORDER_WITH, parentField!!) { ctx ->
            ctx.setExt("a", parentField)
            ctx.setExt("b", parentField)
        }
        assertNotNull("FIELD_ORDER_WITH should not be null", result)
        val cmp = result!!.toIntOrNull()
        assertNotNull("FIELD_ORDER_WITH should resolve to an int. Was: $result", cmp)
        assertEquals(
            "Fields from the same class should compare equal (cmp == 0). Was: $cmp",
            0,
            cmp
        )
    }

    fun testParentFieldsBeforeChildFields() = runTest {
        val psiClass = findClass("com.itangcent.fieldorder.ChildDTO")
        assertNotNull("Should find ChildDTO", psiClass)

        val helper = PsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass!!)
        assertNotNull("Should build object model", model)

        val fields = model?.asObject()?.fields
        assertNotNull("Should have fields", fields)

        val fieldNames = fields!!.keys.toList()
        assertEquals("Should have 2 fields", 2, fieldNames.size)

        // The parent-first rule puts parent class fields before child class fields.
        assertTrue("Should have both fields", fieldNames.contains("childField") && fieldNames.contains("parentField"))
        assertEquals("First field should be 'parentField'", "parentField", fieldNames[0])
        assertEquals("Second field should be 'childField'", "childField", fieldNames[1])
    }
}
