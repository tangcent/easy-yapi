package com.itangcent.easyapi.core.config.source

import com.itangcent.easyapi.core.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.core.psi.PsiClassHelper
import com.itangcent.easyapi.core.rule.RuleKeys
import com.itangcent.easyapi.core.rule.engine.RuleEngine
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Integration test for the `field-order-alphabetically-desc` extension.
 *
 * The extension defines a rule for [RuleKeys.FIELD_ORDER_WITH]:
 *   - `field.order.with=groovy: return -a.name().compareTo(b.name())`
 *
 * The comparator returns the negation of `a.name().compareTo(b.name())`, producing
 * descending alphabetical order (Z-A).
 */
class FieldOrderAlphabeticallyDescConfigIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

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
        loadFile("api/fieldorder/OrderDTO.java")
    }

    override fun createConfigReader(): TestConfigReader {
        val extension = ExtensionConfigRegistry.getExtension("field-order-alphabetically-desc")
        assertNotNull("field-order-alphabetically-desc extension should exist", extension)
        return TestConfigReader.fromConfigText(project, extension?.content ?: "")
    }

    fun testFieldOrderAlphabeticallyDescConfigLoadsCorrectly() = runTest {
        val extension = ExtensionConfigRegistry.getExtension("field-order-alphabetically-desc")
        assertNotNull(extension)
        assertEquals("field-order-alphabetically-desc", extension?.code)
        assertTrue(extension?.content?.isNotBlank() == true)
    }

    /**
     * The core rule: comparing "zebra" (a) against "apple" (b) should resolve
     * [RuleKeys.FIELD_ORDER_WITH] to a negative value (zebra comes first).
     */
    fun testFieldOrderWithRuleZebraBeforeApple() = runTest {
        val psiClass = findClass("com.itangcent.fieldorder.OrderDTO")
        assertNotNull("Should find OrderDTO", psiClass)

        val appleField = psiClass!!.fields.find { it.name == "apple" }
        assertNotNull("Should find apple field", appleField)
        val zebraField = psiClass.fields.find { it.name == "zebra" }
        assertNotNull("Should find zebra field", zebraField)

        val ruleEngine = RuleEngine.getInstance(project)
        val result = ruleEngine.evaluate(RuleKeys.FIELD_ORDER_WITH, zebraField!!) { ctx ->
            ctx.setExt("a", zebraField)
            ctx.setExt("b", appleField)
        }
        assertNotNull("FIELD_ORDER_WITH should not be null", result)
        val cmp = result!!.toIntOrNull()
        assertNotNull("FIELD_ORDER_WITH should resolve to an int. Was: $result", cmp)
        assertTrue(
            "zebra should come before apple in desc order (cmp < 0). Was: $cmp",
            cmp!! < 0
        )
    }

    /**
     * The core rule: comparing "apple" (a) against "zebra" (b) should resolve
     * [RuleKeys.FIELD_ORDER_WITH] to a positive value (apple comes after).
     */
    fun testFieldOrderWithRuleAppleAfterZebra() = runTest {
        val psiClass = findClass("com.itangcent.fieldorder.OrderDTO")
        assertNotNull("Should find OrderDTO", psiClass)

        val appleField = psiClass!!.fields.find { it.name == "apple" }
        assertNotNull("Should find apple field", appleField)
        val zebraField = psiClass.fields.find { it.name == "zebra" }
        assertNotNull("Should find zebra field", zebraField)

        val ruleEngine = RuleEngine.getInstance(project)
        val result = ruleEngine.evaluate(RuleKeys.FIELD_ORDER_WITH, appleField!!) { ctx ->
            ctx.setExt("a", appleField)
            ctx.setExt("b", zebraField)
        }
        assertNotNull("FIELD_ORDER_WITH should not be null", result)
        val cmp = result!!.toIntOrNull()
        assertNotNull("FIELD_ORDER_WITH should resolve to an int. Was: $result", cmp)
        assertTrue(
            "apple should come after zebra in desc order (cmp > 0). Was: $cmp",
            cmp!! > 0
        )
    }

    /**
     * The core rule: comparing two fields with the same name should resolve
     * [RuleKeys.FIELD_ORDER_WITH] to `0` (same order).
     */
    fun testFieldOrderWithRuleSameNameFields() = runTest {
        val psiClass = findClass("com.itangcent.fieldorder.OrderDTO")
        assertNotNull("Should find OrderDTO", psiClass)

        val appleField = psiClass!!.fields.find { it.name == "apple" }
        assertNotNull("Should find apple field", appleField)

        val ruleEngine = RuleEngine.getInstance(project)
        val result = ruleEngine.evaluate(RuleKeys.FIELD_ORDER_WITH, appleField!!) { ctx ->
            ctx.setExt("a", appleField)
            ctx.setExt("b", appleField)
        }
        assertNotNull("FIELD_ORDER_WITH should not be null", result)
        val cmp = result!!.toIntOrNull()
        assertNotNull("FIELD_ORDER_WITH should resolve to an int. Was: $result", cmp)
        assertEquals(
            "Fields with the same name should compare equal (cmp == 0). Was: $cmp",
            0,
            cmp
        )
    }

    fun testFieldsOrderedAlphabeticallyDesc() = runTest {
        val psiClass = findClass("com.itangcent.fieldorder.OrderDTO")
        assertNotNull("Should find OrderDTO", psiClass)

        val helper = PsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass!!)
        assertNotNull("Should build object model", model)

        val fields = model?.asObject()?.fields
        assertNotNull("Should have fields", fields)

        val fieldNames = fields!!.keys.toList()
        assertEquals("Should have 4 fields", 4, fieldNames.size)

        // Expected descending alphabetical order: zebra, mango, banana, apple
        assertEquals("First field should be 'zebra'", "zebra", fieldNames[0])
        assertEquals("Second field should be 'mango'", "mango", fieldNames[1])
        assertEquals("Third field should be 'banana'", "banana", fieldNames[2])
        assertEquals("Fourth field should be 'apple'", "apple", fieldNames[3])
    }
}
