package com.itangcent.easyapi.psi.model

import com.itangcent.easyapi.psi.type.JsonType
import org.junit.Assert.*
import org.junit.Test

class ObjectModelBuilderTest {

    @Test
    fun testBuild_empty() {
        val model = ObjectModelBuilder().build()
        assertTrue(model.fields.isEmpty())
    }

    @Test
    fun testStringField() {
        val model = ObjectModelBuilder()
            .stringField("name", "User name", required = true)
            .build()
        assertTrue(model.fields.containsKey("name"))
        val field = model.fields["name"]!!
        assertEquals(JsonType.STRING, (field.model as ObjectModel.Single).type)
        assertEquals("User name", field.comment)
        assertTrue(field.required)
    }

    @Test
    fun testIntField() {
        val model = ObjectModelBuilder()
            .intField("age", "User age")
            .build()
        val field = model.fields["age"]!!
        assertEquals(JsonType.INT, (field.model as ObjectModel.Single).type)
        assertEquals("User age", field.comment)
    }

    @Test
    fun testLongField() {
        val model = ObjectModelBuilder()
            .longField("id")
            .build()
        val field = model.fields["id"]!!
        assertEquals(JsonType.LONG, (field.model as ObjectModel.Single).type)
    }

    @Test
    fun testFloatField() {
        val model = ObjectModelBuilder()
            .floatField("price")
            .build()
        val field = model.fields["price"]!!
        assertEquals(JsonType.FLOAT, (field.model as ObjectModel.Single).type)
    }

    @Test
    fun testDoubleField() {
        val model = ObjectModelBuilder()
            .doubleField("score")
            .build()
        val field = model.fields["score"]!!
        assertEquals(JsonType.DOUBLE, (field.model as ObjectModel.Single).type)
    }

    @Test
    fun testBooleanField() {
        val model = ObjectModelBuilder()
            .booleanField("active", "Is active")
            .build()
        val field = model.fields["active"]!!
        assertEquals(JsonType.BOOLEAN, (field.model as ObjectModel.Single).type)
        assertEquals("Is active", field.comment)
    }

    @Test
    fun testArrayField() {
        val itemType = ObjectModel.single("string")
        val model = ObjectModelBuilder()
            .arrayField("tags", itemType, "Tag list")
            .build()
        val field = model.fields["tags"]!!
        assertTrue(field.model is ObjectModel.Array)
        assertEquals(itemType, (field.model as ObjectModel.Array).item)
        assertEquals("Tag list", field.comment)
    }

    @Test
    fun testObjectField() {
        val inner = ObjectModelBuilder().stringField("street").build()
        val model = ObjectModelBuilder()
            .objectField("address", inner, "User address")
            .build()
        val field = model.fields["address"]!!
        assertTrue(field.model is ObjectModel.Object)
        assertEquals("User address", field.comment)
    }

    @Test
    fun testMapField() {
        val keyType = ObjectModel.single("string")
        val valueType = ObjectModel.single("int")
        val model = ObjectModelBuilder()
            .mapField("metadata", keyType, valueType, "Metadata")
            .build()
        val field = model.fields["metadata"]!!
        assertTrue(field.model is ObjectModel.MapModel)
        assertEquals("Metadata", field.comment)
    }

    @Test
    fun testField_withAllOptions() {
        val options = listOf(FieldOption(1, "one"), FieldOption(2, "two"))
        val model = ObjectModelBuilder()
            .field(
                name = "status",
                model = ObjectModel.single("int"),
                comment = "Status code",
                required = true,
                defaultValue = "1",
                options = options,
                mock = "@integer(1,2)",
                demo = "1",
                advanced = mapOf("format" to "int32")
            )
            .build()
        val field = model.fields["status"]!!
        assertEquals("Status code", field.comment)
        assertTrue(field.required)
        assertEquals("1", field.defaultValue)
        assertEquals(2, field.options!!.size)
        assertEquals("@integer(1,2)", field.mock)
        assertEquals("1", field.demo)
        assertEquals(mapOf("format" to "int32"), field.advanced)
    }

    @Test
    fun testChaining() {
        val model = ObjectModelBuilder()
            .stringField("name")
            .intField("age")
            .booleanField("active")
            .build()
        assertEquals(3, model.fields.size)
        assertTrue(model.fields.containsKey("name"))
        assertTrue(model.fields.containsKey("age"))
        assertTrue(model.fields.containsKey("active"))
    }
}
