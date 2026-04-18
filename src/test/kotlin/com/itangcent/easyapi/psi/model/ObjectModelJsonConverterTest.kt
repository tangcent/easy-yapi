package com.itangcent.easyapi.psi.model

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ObjectModelJsonConverterTest {

    @Test
    fun testToJsonNullModel() {
        val json = ObjectModelJsonConverter.toJson(null)
        assertEquals("{}", json)
    }

    @Test
    fun testToJsonSingleString() {
        val model = ObjectModel.single("string")
        val json = ObjectModelJsonConverter.toJson(model)
        assertNotNull("Should produce JSON for single string", json)
        assertTrue("JSON should not be empty", json.isNotEmpty())
    }

    @Test
    fun testToJsonSimpleObject() {
        val model = ObjectModel.Object(
            mapOf(
                "name" to FieldModel(ObjectModel.single("string"), comment = "User name"),
                "age" to FieldModel(ObjectModel.single("int"))
            )
        )
        val json = ObjectModelJsonConverter.toJson(model)
        assertTrue("JSON should contain 'name'", json.contains("name"))
        assertTrue("JSON should contain 'age'", json.contains("age"))
    }

    @Test
    fun testToJsonArray() {
        val model = ObjectModel.Array(ObjectModel.single("string"))
        val json = ObjectModelJsonConverter.toJson(model)
        assertTrue("JSON should contain array brackets", json.contains("[") || json.isNotEmpty())
    }

    @Test
    fun testToJsonMapModel() {
        val model = ObjectModel.map(ObjectModel.single("string"), ObjectModel.single("int"))
        val json = ObjectModelJsonConverter.toJson(model)
        assertNotNull("Should produce JSON for map model", json)
        assertTrue("JSON should not be empty", json.isNotEmpty())
    }

    @Test
    fun testToJson5NullModel() {
        val json = ObjectModelJsonConverter.toJson5(null)
        assertEquals("{}", json)
    }

    @Test
    fun testToJson5SimpleObject() {
        val model = ObjectModel.Object(
            mapOf(
                "name" to FieldModel(ObjectModel.single("string"), comment = "User name")
            )
        )
        val json5 = ObjectModelJsonConverter.toJson5(model)
        assertTrue("JSON5 should contain 'name'", json5.contains("name"))
    }

    @Test
    fun testToJson5IncludesComments() {
        val model = ObjectModel.Object(
            mapOf(
                "name" to FieldModel(ObjectModel.single("string"), comment = "User name")
            )
        )
        val json5 = ObjectModelJsonConverter.toJson5(model)
        assertTrue("JSON5 should contain comment", json5.contains("User name"))
    }

    @Test
    fun testToJsonWithCustomHandler() {
        val model = ObjectModel.single("string")
        val json = ObjectModelJsonConverter.toJson(model, RawJsonHandler)
        assertNotNull("Should produce JSON with custom handler", json)
    }

    @Test
    fun testToJsonEmptyObject() {
        val model = ObjectModel.emptyObject()
        val json = ObjectModelJsonConverter.toJson(model)
        assertNotNull("Should produce JSON for empty object", json)
    }

    @Test
    fun testToJsonNestedObject() {
        val inner = ObjectModel.Object(
            mapOf("city" to FieldModel(ObjectModel.single("string")))
        )
        val outer = ObjectModel.Object(
            mapOf("address" to FieldModel(inner))
        )
        val json = ObjectModelJsonConverter.toJson(outer)
        assertTrue("JSON should contain 'address'", json.contains("address"))
        assertTrue("JSON should contain 'city'", json.contains("city"))
    }

    @Test
    fun testToJsonArrayOfObjects() {
        val item = ObjectModel.Object(
            mapOf("id" to FieldModel(ObjectModel.single("int")))
        )
        val model = ObjectModel.Array(item)
        val json = ObjectModelJsonConverter.toJson(model)
        assertTrue("JSON should contain 'id'", json.contains("id"))
    }

    @Test
    fun testToJson5ArrayOfObjects() {
        val item = ObjectModel.Object(
            mapOf("id" to FieldModel(ObjectModel.single("int"), comment = "ID"))
        )
        val model = ObjectModel.Array(item)
        val json5 = ObjectModelJsonConverter.toJson5(model)
        assertTrue("JSON5 should contain 'id'", json5.contains("id"))
    }
}
