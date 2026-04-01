package com.itangcent.easyapi.ide.action

import com.itangcent.easyapi.util.GsonUtils
import junit.framework.TestCase

class FieldsToJsonActionTest : TestCase() {

    private val action = FieldsToJsonAction()

    fun testActionInstance() {
        assertNotNull(action)
    }

    fun testJsonFormattingWithSimpleMap() {
        val model = mapOf(
            "name" to "test",
            "value" to 123,
            "enabled" to true
        )
        val json = GsonUtils.prettyJson(model)
        assertTrue(json.contains("\"name\""))
        assertTrue(json.contains("\"test\""))
        assertTrue(json.contains("\"value\""))
        assertTrue(json.contains("123"))
        assertTrue(json.contains("\"enabled\""))
        assertTrue(json.contains("true"))
    }

    fun testJsonFormattingWithNestedMap() {
        val nested = mapOf(
            "inner" to "value",
            "count" to 42
        )
        val model = mapOf(
            "outer" to nested,
            "simple" to "string"
        )
        val json = GsonUtils.prettyJson(model)
        assertTrue(json.contains("\"outer\""))
        assertTrue(json.contains("\"inner\""))
        assertTrue(json.contains("\"simple\""))
    }

    fun testJsonFormattingWithList() {
        val model = mapOf(
            "items" to listOf("a", "b", "c"),
            "count" to 3
        )
        val json = GsonUtils.prettyJson(model)
        assertTrue(json.contains("\"items\""))
        assertTrue(json.contains("\"a\""))
        assertTrue(json.contains("\"b\""))
        assertTrue(json.contains("\"c\""))
    }

    fun testJsonFormattingWithNullValue() {
        val model = mapOf<String, Any?>(
            "name" to "test",
            "nullable" to null
        )
        val json = GsonUtils.prettyJson(model)
        assertTrue(json.contains("\"name\""))
        // Gson by default does not serialize null values
        assertFalse(json.contains("nullable"))
    }

    fun testJsonFormattingWithEmptyMap() {
        val model = emptyMap<String, Any?>()
        val json = GsonUtils.prettyJson(model)
        assertEquals("{}", json.trim())
    }

    fun testJsonFormattingWithEmptyList() {
        val model = mapOf<String, Any?>(
            "items" to emptyList<Any?>()
        )
        val json = GsonUtils.prettyJson(model)
        assertTrue(json.contains("\"items\""))
        assertTrue(json.contains("[]"))
    }

    fun testJsonFormattingWithPrimitiveTypes() {
        val model = mapOf<String, Any?>(
            "intVal" to 0,
            "longVal" to 0L,
            "doubleVal" to 0.0,
            "floatVal" to 0.0f,
            "boolVal" to false,
            "byteVal" to 0.toByte(),
            "shortVal" to 0.toShort()
        )
        val json = GsonUtils.prettyJson(model)
        assertTrue(json.contains("\"intVal\": 0"))
        assertTrue(json.contains("\"longVal\": 0"))
        assertTrue(json.contains("\"doubleVal\": 0.0"))
        assertTrue(json.contains("\"floatVal\": 0.0"))
        assertTrue(json.contains("\"boolVal\": false"))
    }

    fun testJsonFormattingPreservesFieldOrder() {
        val model = linkedMapOf<String, Any?>(
            "first" to 1,
            "second" to 2,
            "third" to 3
        )
        val json = GsonUtils.prettyJson(model)
        val firstIndex = json.indexOf("\"first\"")
        val secondIndex = json.indexOf("\"second\"")
        val thirdIndex = json.indexOf("\"third\"")
        assertTrue(firstIndex < secondIndex)
        assertTrue(secondIndex < thirdIndex)
    }
}
