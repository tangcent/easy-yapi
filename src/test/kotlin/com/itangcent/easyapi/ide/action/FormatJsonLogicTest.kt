package com.itangcent.easyapi.ide.action

import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.FieldOption
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.JsonType
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests the private formatJson functions in FieldsToJsonAction.kt
 * via reflection on the compiled FieldsToJsonActionKt class.
 *
 * These are pure functions that convert ObjectModel to JSON string,
 * so they can be tested without IntelliJ platform dependencies.
 */
class FormatJsonLogicTest {

    private val fieldsToJsonActionKtClass =
        Class.forName("com.itangcent.easyapi.ide.action.FieldsToJsonActionKt")

    // formatJson(model: ObjectModel): String
    private val formatJsonMethod = fieldsToJsonActionKtClass
        .getDeclaredMethod("formatJson", ObjectModel::class.java)
        .apply { isAccessible = true }

    // formatJson(model: ObjectModel, deep: Int, sb: StringBuilder): void
    private val formatJsonDeepMethod = fieldsToJsonActionKtClass
        .getDeclaredMethod("formatJson", ObjectModel::class.java, Int::class.java, StringBuilder::class.java)
        .apply { isAccessible = true }

    private fun formatJson(model: ObjectModel): String {
        return formatJsonMethod.invoke(null, model) as String
    }

    @Test
    fun testFormatJsonStringType() {
        val model = ObjectModel.single(JsonType.STRING)
        val result = formatJson(model)
        assertEquals("\"\"", result)
    }

    @Test
    fun testFormatJsonIntType() {
        val model = ObjectModel.single(JsonType.INT)
        val result = formatJson(model)
        assertEquals("0", result)
    }

    @Test
    fun testFormatJsonLongType() {
        val model = ObjectModel.single(JsonType.LONG)
        val result = formatJson(model)
        assertEquals("0", result)
    }

    @Test
    fun testFormatJsonFloatType() {
        val model = ObjectModel.single(JsonType.FLOAT)
        val result = formatJson(model)
        assertEquals("0.0", result)
    }

    @Test
    fun testFormatJsonDoubleType() {
        val model = ObjectModel.single(JsonType.DOUBLE)
        val result = formatJson(model)
        assertEquals("0.0", result)
    }

    @Test
    fun testFormatJsonBooleanType() {
        val model = ObjectModel.single(JsonType.BOOLEAN)
        val result = formatJson(model)
        assertEquals("false", result)
    }

    @Test
    fun testFormatJsonUnknownType() {
        val model = ObjectModel.single("unknown_type")
        val result = formatJson(model)
        assertEquals("null", result)
    }

    @Test
    fun testFormatJsonEmptyObject() {
        val model = ObjectModel.emptyObject()
        val result = formatJson(model)
        assertEquals("{}", result)
    }

    @Test
    fun testFormatJsonObjectWithFields() {
        val model = ObjectModel.Object(
            mapOf(
                "name" to FieldModel(ObjectModel.single(JsonType.STRING)),
                "age" to FieldModel(ObjectModel.single(JsonType.INT))
            )
        )
        val result = formatJson(model)
        assertTrue("Result should contain 'name'", result.contains("\"name\""))
        assertTrue("Result should contain 'age'", result.contains("\"age\""))
        assertTrue("Result should contain string default", result.contains("\"\""))
        assertTrue("Result should contain int default", result.contains("0"))
        assertTrue("Result should start with {", result.trimStart().startsWith("{"))
        assertTrue("Result should end with }", result.trimEnd().endsWith("}"))
    }

    @Test
    fun testFormatJsonArray() {
        val model = ObjectModel.array(ObjectModel.single(JsonType.STRING))
        val result = formatJson(model)
        assertTrue("Result should start with [", result.trimStart().startsWith("["))
        assertTrue("Result should end with ]", result.trimEnd().endsWith("]"))
        assertTrue("Result should contain string default", result.contains("\"\""))
    }

    @Test
    fun testFormatJsonArrayOfObjects() {
        val itemModel = ObjectModel.Object(
            mapOf(
                "id" to FieldModel(ObjectModel.single(JsonType.INT))
            )
        )
        val model = ObjectModel.array(itemModel)
        val result = formatJson(model)
        assertTrue("Result should start with [", result.trimStart().startsWith("["))
        assertTrue("Result should contain 'id'", result.contains("\"id\""))
    }

    @Test
    fun testFormatJsonMap() {
        val model = ObjectModel.map(
            ObjectModel.single(JsonType.STRING),
            ObjectModel.single(JsonType.INT)
        )
        val result = formatJson(model)
        assertTrue("Result should start with {", result.trimStart().startsWith("{"))
        assertTrue("Result should contain 'key'", result.contains("\"key\""))
        assertTrue("Result should contain 'value'", result.contains("\"value\""))
    }

    @Test
    fun testFormatJsonNestedObject() {
        val inner = ObjectModel.Object(
            mapOf(
                "city" to FieldModel(ObjectModel.single(JsonType.STRING))
            )
        )
        val outer = ObjectModel.Object(
            mapOf(
                "name" to FieldModel(ObjectModel.single(JsonType.STRING)),
                "address" to FieldModel(inner)
            )
        )
        val result = formatJson(outer)
        assertTrue("Result should contain 'name'", result.contains("\"name\""))
        assertTrue("Result should contain 'address'", result.contains("\"address\""))
        assertTrue("Result should contain 'city'", result.contains("\"city\""))
    }

    @Test
    fun testFormatJsonObjectWithBooleanField() {
        val model = ObjectModel.Object(
            mapOf(
                "active" to FieldModel(ObjectModel.single(JsonType.BOOLEAN))
            )
        )
        val result = formatJson(model)
        assertTrue("Result should contain 'active'", result.contains("\"active\""))
        assertTrue("Result should contain 'false'", result.contains("false"))
    }

    @Test
    fun testFormatJsonObjectWithMultipleTypes() {
        val model = ObjectModel.Object(
            mapOf(
                "name" to FieldModel(ObjectModel.single(JsonType.STRING)),
                "count" to FieldModel(ObjectModel.single(JsonType.INT)),
                "price" to FieldModel(ObjectModel.single(JsonType.DOUBLE)),
                "enabled" to FieldModel(ObjectModel.single(JsonType.BOOLEAN))
            )
        )
        val result = formatJson(model)
        assertTrue("Result should contain 'name'", result.contains("\"name\""))
        assertTrue("Result should contain 'count'", result.contains("\"count\""))
        assertTrue("Result should contain 'price'", result.contains("\"price\""))
        assertTrue("Result should contain 'enabled'", result.contains("\"enabled\""))
    }

    @Test
    fun testFormatJsonNullType() {
        val model = ObjectModel.nullValue()
        val result = formatJson(model)
        assertEquals("null", result)
    }

    @Test
    fun testFormatJsonMapWithComplexValues() {
        val model = ObjectModel.map(
            ObjectModel.single(JsonType.STRING),
            ObjectModel.Object(
                mapOf(
                    "id" to FieldModel(ObjectModel.single(JsonType.INT)),
                    "label" to FieldModel(ObjectModel.single(JsonType.STRING))
                )
            )
        )
        val result = formatJson(model)
        assertTrue("Result should contain 'key'", result.contains("\"key\""))
        assertTrue("Result should contain 'value'", result.contains("\"value\""))
        assertTrue("Result should contain 'id'", result.contains("\"id\""))
        assertTrue("Result should contain 'label'", result.contains("\"label\""))
    }

    @Test
    fun testFormatJsonArrayInObject() {
        val model = ObjectModel.Object(
            mapOf(
                "tags" to FieldModel(ObjectModel.array(ObjectModel.single(JsonType.STRING)))
            )
        )
        val result = formatJson(model)
        assertTrue("Result should contain 'tags'", result.contains("\"tags\""))
        assertTrue("Result should contain array brackets", result.contains("["))
    }
}
