package com.itangcent.easyapi.psi.model

import com.itangcent.easyapi.psi.type.JsonType
import org.junit.Assert.*
import org.junit.Test

class RawJsonHandlerTest {

    private fun field(type: String = JsonType.STRING): FieldModel {
        return FieldModel(model = ObjectModel.Single(type))
    }

    @Test
    fun testBeforeObjectStart() {
        val sb = StringBuilder()
        RawJsonHandler.beforeObjectStart(sb, 0)
        assertEquals("{", sb.toString())
    }

    @Test
    fun testAfterObjectEnd() {
        val sb = StringBuilder()
        RawJsonHandler.afterObjectEnd(sb, 0)
        assertEquals("}", sb.toString())
    }

    @Test
    fun testAfterObjectEnd_indented() {
        val sb = StringBuilder()
        RawJsonHandler.afterObjectEnd(sb, 2)
        assertEquals("    }", sb.toString())
    }

    @Test
    fun testBeforeObjectField_first() {
        val sb = StringBuilder()
        RawJsonHandler.beforeObjectField(sb, "name", field(), 0, 2, 0)
        assertTrue(sb.toString().startsWith("\n"))
        assertTrue(sb.toString().contains("\"name\": "))
    }

    @Test
    fun testBeforeObjectField_notFirst() {
        val sb = StringBuilder()
        RawJsonHandler.beforeObjectField(sb, "age", field(JsonType.INT), 1, 2, 0)
        assertFalse(sb.toString().startsWith("\n"))
        assertTrue(sb.toString().contains("\"age\": "))
    }

    @Test
    fun testAfterObjectField_notLast() {
        val sb = StringBuilder()
        RawJsonHandler.afterObjectField(sb, "name", field(), 0, 2, 0)
        assertTrue(sb.toString().contains(","))
    }

    @Test
    fun testAfterObjectField_last() {
        val sb = StringBuilder()
        RawJsonHandler.afterObjectField(sb, "name", field(), 1, 2, 0)
        assertFalse(sb.toString().contains(","))
        assertTrue(sb.toString().contains("\n"))
    }

    @Test
    fun testBeforeArrayStart() {
        val sb = StringBuilder()
        RawJsonHandler.beforeArrayStart(sb, 0)
        assertEquals("[", sb.toString())
    }

    @Test
    fun testAfterArrayEnd() {
        val sb = StringBuilder()
        RawJsonHandler.afterArrayEnd(sb, 0)
        assertEquals("]", sb.toString())
    }

    @Test
    fun testAfterArrayEnd_indented() {
        val sb = StringBuilder()
        RawJsonHandler.afterArrayEnd(sb, 1)
        assertEquals("  ]", sb.toString())
    }

    @Test
    fun testBeforeArrayItem() {
        val sb = StringBuilder()
        val item = ObjectModel.Single(JsonType.STRING)
        RawJsonHandler.beforeArrayItem(sb, item, 0, 1, 0)
        assertTrue(sb.toString().contains("\n"))
    }

    @Test
    fun testAfterArrayItem() {
        val sb = StringBuilder()
        val item = ObjectModel.Single(JsonType.STRING)
        RawJsonHandler.afterArrayItem(sb, item, 0, 1, 0)
        assertTrue(sb.toString().contains("\n"))
    }

    @Test
    fun testBeforeMapStart() {
        val sb = StringBuilder()
        RawJsonHandler.beforeMapStart(sb, 0)
        assertEquals("{", sb.toString())
    }

    @Test
    fun testAfterMapEnd() {
        val sb = StringBuilder()
        RawJsonHandler.afterMapEnd(sb, 0)
        assertEquals("}", sb.toString())
    }

    @Test
    fun testBeforeMapKey() {
        val sb = StringBuilder()
        RawJsonHandler.beforeMapKey(sb, 0)
        assertTrue(sb.toString().contains("\n"))
    }

    @Test
    fun testBetweenMapKeyAndValue() {
        val sb = StringBuilder()
        RawJsonHandler.betweenMapKeyAndValue(sb, 0)
        assertEquals(": ", sb.toString())
    }

    @Test
    fun testAfterMapValue() {
        val sb = StringBuilder()
        RawJsonHandler.afterMapValue(sb, 0)
        assertTrue(sb.toString().contains("\n"))
    }

    @Test
    fun testHandleSingleValue_string() {
        val sb = StringBuilder()
        RawJsonHandler.handleSingleValue(sb, ObjectModel.Single(JsonType.STRING), 0)
        assertEquals("\"\"", sb.toString())
    }

    @Test
    fun testHandleSingleValue_int() {
        val sb = StringBuilder()
        RawJsonHandler.handleSingleValue(sb, ObjectModel.Single(JsonType.INT), 0)
        assertEquals("0", sb.toString())
    }

    @Test
    fun testHandleSingleValue_boolean() {
        val sb = StringBuilder()
        RawJsonHandler.handleSingleValue(sb, ObjectModel.Single(JsonType.BOOLEAN), 0)
        assertEquals("false", sb.toString())
    }

    @Test
    fun testHandleSingleValue_double() {
        val sb = StringBuilder()
        RawJsonHandler.handleSingleValue(sb, ObjectModel.Single(JsonType.DOUBLE), 0)
        assertEquals("0.0", sb.toString())
    }

    @Test
    fun testHandleSingleValue_null() {
        val sb = StringBuilder()
        RawJsonHandler.handleSingleValue(sb, ObjectModel.Single("null"), 0)
        assertEquals("null", sb.toString())
    }
}
