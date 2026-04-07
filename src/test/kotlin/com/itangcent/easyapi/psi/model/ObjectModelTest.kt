package com.itangcent.easyapi.psi.model

import org.junit.Assert.*
import org.junit.Test

class ObjectModelTest {

    @Test
    fun testSingle_creation() {
        val model = ObjectModel.single("string")
        assertTrue(model.isSingle())
        assertFalse(model.isObject())
        assertFalse(model.isArray())
        assertFalse(model.isMap())
        assertEquals("string", model.type)
    }

    @Test
    fun testSingle_asSingle() {
        val model = ObjectModel.single("int")
        assertNotNull(model.asSingle())
        assertNull(model.asObject())
        assertNull(model.asArray())
        assertNull(model.asMap())
    }

    @Test
    fun testNullValue() {
        val model = ObjectModel.nullValue()
        assertEquals("null", model.type)
        assertTrue(model.isSingle())
    }

    @Test
    fun testObject_creation() {
        val fields = mapOf("name" to FieldModel(ObjectModel.single("string")))
        val model = ObjectModel.Object(fields)
        assertTrue(model.isObject())
        assertFalse(model.isSingle())
        assertFalse(model.isArray())
        assertFalse(model.isMap())
        assertEquals(fields, model.fields)
    }

    @Test
    fun testObject_emptyObject() {
        val model = ObjectModel.emptyObject()
        assertTrue(model.isObject())
        assertTrue(model.fields.isEmpty())
    }

    @Test
    fun testObject_equality_byId() {
        val fields = mapOf("name" to FieldModel(ObjectModel.single("string")))
        val obj1 = ObjectModel.Object(fields)
        val obj2 = ObjectModel.Object(fields)
        // Objects are equal only if same id
        assertNotEquals(obj1, obj2)
        assertEquals(obj1, obj1)
    }

    @Test
    fun testObject_hashCode_byId() {
        val obj = ObjectModel.Object(emptyMap())
        assertEquals(obj.id.hashCode(), obj.hashCode())
    }

    @Test
    fun testObject_asObject() {
        val model = ObjectModel.Object(emptyMap())
        assertNotNull(model.asObject())
        assertNull(model.asSingle())
        assertNull(model.asArray())
        assertNull(model.asMap())
    }

    @Test
    fun testArray_creation() {
        val item = ObjectModel.single("string")
        val model = ObjectModel.array(item)
        assertTrue(model.isArray())
        assertFalse(model.isSingle())
        assertFalse(model.isObject())
        assertFalse(model.isMap())
        assertEquals(item, model.item)
    }

    @Test
    fun testArray_asArray() {
        val model = ObjectModel.array(ObjectModel.single("int"))
        assertNotNull(model.asArray())
        assertNull(model.asSingle())
        assertNull(model.asObject())
        assertNull(model.asMap())
    }

    @Test
    fun testMapModel_creation() {
        val keyType = ObjectModel.single("string")
        val valueType = ObjectModel.single("int")
        val model = ObjectModel.map(keyType, valueType)
        assertTrue(model.isMap())
        assertFalse(model.isSingle())
        assertFalse(model.isObject())
        assertFalse(model.isArray())
        assertEquals(keyType, model.keyType)
        assertEquals(valueType, model.valueType)
    }

    @Test
    fun testMapModel_asMap() {
        val model = ObjectModel.map(ObjectModel.single("string"), ObjectModel.single("any"))
        assertNotNull(model.asMap())
        assertNull(model.asSingle())
        assertNull(model.asObject())
        assertNull(model.asArray())
    }

    @Test
    fun testObject_flattenFields_simple() {
        val obj = ObjectModel.Object(mapOf(
            "name" to FieldModel(ObjectModel.single("string")),
            "age" to FieldModel(ObjectModel.single("int"))
        ))
        val flat = obj.flattenFields()
        assertEquals(2, flat.size)
        assertTrue(flat.containsKey("name"))
        assertTrue(flat.containsKey("age"))
    }

    @Test
    fun testObject_flattenFields_nested() {
        val inner = ObjectModel.Object(mapOf(
            "street" to FieldModel(ObjectModel.single("string"))
        ))
        val outer = ObjectModel.Object(mapOf(
            "name" to FieldModel(ObjectModel.single("string")),
            "address" to FieldModel(inner)
        ))
        val flat = outer.flattenFields()
        assertTrue(flat.containsKey("name"))
        assertTrue(flat.containsKey("address.street"))
    }

    @Test
    fun testObject_flattenFields_withArray() {
        val item = ObjectModel.single("string")
        val obj = ObjectModel.Object(mapOf(
            "tags" to FieldModel(ObjectModel.array(item))
        ))
        val flat = obj.flattenFields()
        assertTrue(flat.containsKey("tags[0]"))
    }

    @Test
    fun testObject_flattenFields_maxDepth() {
        // Create deeply nested structure
        var model: ObjectModel = ObjectModel.single("string")
        for (i in 0..10) {
            model = ObjectModel.Object(mapOf("nested" to FieldModel(model)))
        }
        val root = model as ObjectModel.Object
        val flat = root.flattenFields(maxDepth = 3)
        // Should not go beyond maxDepth
        assertNotNull(flat)
    }
}

class FieldModelTest {

    @Test
    fun testDefaultValues() {
        val model = FieldModel(ObjectModel.single("string"))
        assertNull(model.comment)
        assertFalse(model.required)
        assertNull(model.defaultValue)
        assertNull(model.options)
        assertNull(model.mock)
        assertNull(model.demo)
        assertNull(model.advanced)
        assertFalse(model.generic)
    }

    @Test
    fun testCustomValues() {
        val options = listOf(FieldOption(1, "one"), FieldOption(2, "two"))
        val model = FieldModel(
            model = ObjectModel.single("int"),
            comment = "User age",
            required = true,
            defaultValue = "0",
            options = options,
            mock = "@integer(0,100)",
            demo = "25",
            advanced = mapOf("format" to "int32"),
            generic = true
        )
        assertEquals("User age", model.comment)
        assertTrue(model.required)
        assertEquals("0", model.defaultValue)
        assertEquals(2, model.options!!.size)
        assertEquals("@integer(0,100)", model.mock)
        assertEquals("25", model.demo)
        assertEquals(mapOf("format" to "int32"), model.advanced)
        assertTrue(model.generic)
    }

    @Test
    fun testToString() {
        val model = FieldModel(ObjectModel.single("string"), comment = "test")
        val str = model.toString()
        assertTrue(str.contains("FieldModel"))
        assertTrue(str.contains("comment=test"))
    }
}

class FieldOptionTest {

    @Test
    fun testDefaultDesc() {
        val option = FieldOption(1)
        assertEquals(1, option.value)
        assertNull(option.desc)
    }

    @Test
    fun testWithDesc() {
        val option = FieldOption("active", "Active status")
        assertEquals("active", option.value)
        assertEquals("Active status", option.desc)
    }

    @Test
    fun testNullValue() {
        val option = FieldOption(null, "null option")
        assertNull(option.value)
        assertEquals("null option", option.desc)
    }

    @Test
    fun testEquality() {
        val o1 = FieldOption(1, "one")
        val o2 = FieldOption(1, "one")
        assertEquals(o1, o2)
    }

    @Test
    fun testInequality() {
        val o1 = FieldOption(1, "one")
        val o2 = FieldOption(2, "two")
        assertNotEquals(o1, o2)
    }
}
