package com.itangcent.easyapi.psi.model

import com.itangcent.easyapi.psi.type.JsonType
import org.junit.Assert.*
import org.junit.Test

class Json5HandlerTest {

    @Test
    fun testHandleSingleValue_string() {
        val sb = StringBuilder()
        Json5Handler.handleSingleValue(sb, ObjectModel.Single(JsonType.STRING), 0)
        assertEquals("\"\"", sb.toString())
    }

    @Test
    fun testHandleSingleValue_int() {
        val sb = StringBuilder()
        Json5Handler.handleSingleValue(sb, ObjectModel.Single(JsonType.INT), 0)
        assertEquals("0", sb.toString())
    }

    @Test
    fun testHandleSingleValue_boolean() {
        val sb = StringBuilder()
        Json5Handler.handleSingleValue(sb, ObjectModel.Single(JsonType.BOOLEAN), 0)
        assertEquals("false", sb.toString())
    }

    @Test
    fun testHandleSingleValue_null() {
        val sb = StringBuilder()
        Json5Handler.handleSingleValue(sb, ObjectModel.Single("null"), 0)
        assertEquals("null", sb.toString())
    }

    @Test
    fun testBeforeObjectStart() {
        val sb = StringBuilder()
        Json5Handler.beforeObjectStart(sb, 0)
        assertEquals("{", sb.toString())
    }

    @Test
    fun testAfterObjectEnd() {
        val sb = StringBuilder()
        Json5Handler.afterObjectEnd(sb, 0)
        assertEquals("}", sb.toString())
    }

    @Test
    fun testAfterObjectEnd_withIndent() {
        val sb = StringBuilder()
        Json5Handler.afterObjectEnd(sb, 2)
        assertEquals("    }", sb.toString())
    }

    @Test
    fun testBeforeArrayStart() {
        val sb = StringBuilder()
        Json5Handler.beforeArrayStart(sb, 0)
        assertEquals("[", sb.toString())
    }

    @Test
    fun testAfterArrayEnd() {
        val sb = StringBuilder()
        Json5Handler.afterArrayEnd(sb, 0)
        assertEquals("]", sb.toString())
    }

    @Test
    fun testBeforeMapStart() {
        val sb = StringBuilder()
        Json5Handler.beforeMapStart(sb, 0)
        assertEquals("{", sb.toString())
    }

    @Test
    fun testAfterMapEnd() {
        val sb = StringBuilder()
        Json5Handler.afterMapEnd(sb, 0)
        assertEquals("}", sb.toString())
    }

    @Test
    fun testBetweenMapKeyAndValue() {
        val sb = StringBuilder()
        Json5Handler.betweenMapKeyAndValue(sb, 0)
        assertEquals(": ", sb.toString())
    }

    @Test
    fun testBeforeObjectField_withComment() {
        val sb = StringBuilder()
        val field = FieldModel(ObjectModel.single(JsonType.STRING), comment = "User name")
        Json5Handler.beforeObjectField(sb, "name", field, 0, 1, 0)
        val result = sb.toString()
        assertTrue(result.contains("\"name\""))
        assertTrue(result.contains(": "))
    }

    @Test
    fun testAfterObjectField_withEndlineComment() {
        val sb = StringBuilder()
        val field = FieldModel(ObjectModel.single(JsonType.STRING), comment = "User name")
        Json5Handler.afterObjectField(sb, "name", field, 0, 2, 0)
        val result = sb.toString()
        assertTrue(result.contains("// User name"))
    }

    @Test
    fun testAfterObjectField_lastField_noComma() {
        val sb = StringBuilder()
        val field = FieldModel(ObjectModel.single(JsonType.STRING))
        Json5Handler.afterObjectField(sb, "name", field, 0, 1, 0)
        val result = sb.toString()
        assertFalse(result.contains(","))
    }

    @Test
    fun testAfterObjectField_notLastField_hasComma() {
        val sb = StringBuilder()
        val field = FieldModel(ObjectModel.single(JsonType.STRING))
        Json5Handler.afterObjectField(sb, "name", field, 0, 2, 0)
        val result = sb.toString()
        assertTrue(result.contains(","))
    }

    @Test
    fun testBeforeObjectField_multilineComment_usesBlockComment() {
        val sb = StringBuilder()
        val field = FieldModel(ObjectModel.single(JsonType.STRING), comment = "Line 1\nLine 2")
        Json5Handler.beforeObjectField(sb, "name", field, 0, 1, 0)
        val result = sb.toString()
        assertTrue(result.contains("/*"))
        assertTrue(result.contains("*/"))
    }

    @Test
    fun testBeforeObjectField_objectType_usesBlockComment() {
        val sb = StringBuilder()
        val innerObj = ObjectModel.Object(mapOf("x" to FieldModel(ObjectModel.single(JsonType.INT))))
        val field = FieldModel(innerObj, comment = "Address object")
        Json5Handler.beforeObjectField(sb, "address", field, 0, 1, 0)
        val result = sb.toString()
        assertTrue(result.contains("/*"))
    }

    @Test
    fun testAfterObjectField_objectType_noEndlineComment() {
        val sb = StringBuilder()
        val innerObj = ObjectModel.Object(mapOf("x" to FieldModel(ObjectModel.single(JsonType.INT))))
        val field = FieldModel(innerObj, comment = "Address object")
        Json5Handler.afterObjectField(sb, "address", field, 0, 1, 0)
        val result = sb.toString()
        assertFalse(result.contains("//"))
    }

    @Test
    fun testBeforeObjectField_withOptions() {
        val sb = StringBuilder()
        val field = FieldModel(
            ObjectModel.single(JsonType.INT),
            comment = "Status",
            options = listOf(FieldOption(1, "active"), FieldOption(0, "inactive"))
        )
        Json5Handler.beforeObjectField(sb, "status", field, 0, 1, 0)
        // Options cause multi-line comment, so block comment is used
        val result = sb.toString()
        assertTrue(result.contains("/*"))
    }
}
