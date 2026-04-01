package com.itangcent.easyapi.psi

import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.JsonType
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Tests generic type resolution through class inheritance hierarchies.
 */
class GenericInheritanceTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
        loadFile("model/generic/GenericBase.java")
        loadFile("model/generic/StringChild.java")
        loadFile("model/generic/TwoTypeBase.java")
        loadFile("model/generic/MiddleChild.java")
        loadFile("model/generic/ConcreteLeaf.java")
    }

    override fun createConfigReader() = TestConfigReader.EMPTY

    override fun customizeContext(builder: com.itangcent.easyapi.core.context.ActionContextBuilder) {
        builder.bind(DocHelper::class, StandardDocHelper())
    }

    /**
     * Case 1: class GenericBase<T> { T data; }
     *         class StringChild extends GenericBase<String>
     *
     * StringChild should have:
     *   - data: string (inherited from GenericBase, T resolved to String)
     *   - count: int (own field)
     */
    fun testSingleTypeParamInheritance() = runTest {
        val psiClass = findClass("com.itangcent.model.generic.StringChild")
        assertNotNull("Should find StringChild", psiClass)

        val helper = DefaultPsiClassHelper()
        val model = helper.buildObjectModel(psiClass!!, actionContext, maxDepth = 5)
        assertNotNull("Should build model for StringChild", model)

        val obj = model as? ObjectModel.Object
        assertNotNull("Model should be Object", obj)

        // Own field
        val countField = obj!!.fields["count"]
        assertNotNull("Should have 'count' field", countField)
        assertTrue(
            "count should be int, got: ${countField!!.model}",
            countField.model is ObjectModel.Single && (countField.model as ObjectModel.Single).type == JsonType.INT
        )

        // Inherited field with resolved generic
        val dataField = obj.fields["data"]
        assertNotNull("Should have inherited 'data' field", dataField)
        assertTrue(
            "data should be string (T resolved to String), got: ${dataField!!.model}",
            dataField.model is ObjectModel.Single && (dataField.model as ObjectModel.Single).type == JsonType.STRING
        )
    }

    /**
     * Case 2: class TwoTypeBase<T, R> { T first; R second; }
     *         class MiddleChild<X> extends TwoTypeBase<X, String>
     *         class ConcreteLeaf extends MiddleChild<Long>
     *
     * ConcreteLeaf should have:
     *   - first: long (T → X → Long)
     *   - second: string (R → String)
     *   - middleName: string (from MiddleChild)
     *   - active: boolean (own field)
     */
    fun testMultiLevelInheritance() = runTest {
        val psiClass = findClass("com.itangcent.model.generic.ConcreteLeaf")
        assertNotNull("Should find ConcreteLeaf", psiClass)

        val helper = DefaultPsiClassHelper()
        val model = helper.buildObjectModel(psiClass!!, actionContext, maxDepth = 5)
        assertNotNull("Should build model for ConcreteLeaf", model)

        val obj = model as? ObjectModel.Object
        assertNotNull("Model should be Object", obj)

        // Own field
        val activeField = obj!!.fields["active"]
        assertNotNull("Should have 'active' field", activeField)
        assertTrue(
            "active should be boolean, got: ${activeField!!.model}",
            activeField.model is ObjectModel.Single && (activeField.model as ObjectModel.Single).type == JsonType.BOOLEAN
        )

        // MiddleChild's own field
        val middleNameField = obj.fields["middleName"]
        assertNotNull("Should have 'middleName' field from MiddleChild", middleNameField)
        assertTrue(
            "middleName should be string, got: ${middleNameField!!.model}",
            middleNameField.model is ObjectModel.Single && (middleNameField.model as ObjectModel.Single).type == JsonType.STRING
        )

        // Inherited from TwoTypeBase: T first → X → Long
        val firstField = obj.fields["first"]
        assertNotNull("Should have inherited 'first' field", firstField)
        assertTrue(
            "first should be long (T→X→Long), got: ${firstField!!.model}",
            firstField.model is ObjectModel.Single && (firstField.model as ObjectModel.Single).type == JsonType.LONG
        )

        // Inherited from TwoTypeBase: R second → String
        val secondField = obj.fields["second"]
        assertNotNull("Should have inherited 'second' field", secondField)
        assertTrue(
            "second should be string (R→String), got: ${secondField!!.model}",
            secondField.model is ObjectModel.Single && (secondField.model as ObjectModel.Single).type == JsonType.STRING
        )
    }

    /**
     * Verify that comments are preserved through generic inheritance.
     */
    fun testCommentsPreservedThroughInheritance() = runTest {
        val psiClass = findClass("com.itangcent.model.generic.StringChild")
        assertNotNull(psiClass)

        val helper = DefaultPsiClassHelper()
        val model = helper.buildObjectModel(psiClass!!, actionContext, maxDepth = 5) as? ObjectModel.Object
        assertNotNull(model)

        val dataField = model!!.fields["data"]
        assertNotNull(dataField)
        assertNotNull("Inherited field should preserve comment", dataField!!.comment)
        assertTrue(
            "data comment should contain 'the data payload', got: '${dataField.comment}'",
            dataField.comment!!.contains("the data payload")
        )

        val countField = model.fields["count"]
        assertNotNull(countField)
        assertNotNull("Own field should have comment", countField!!.comment)
        assertTrue(
            "count comment should contain 'extra field in child', got: '${countField.comment}'",
            countField.comment!!.contains("extra field in child")
        )
    }
}
