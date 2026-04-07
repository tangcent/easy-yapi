package com.itangcent.easyapi.psi.model

import com.itangcent.easyapi.psi.type.JsonType
import org.junit.Assert.*
import org.junit.Test

class ObjectModelValueConverterTest {

    @Test
    fun testToSimpleValue_null() {
        assertNull(ObjectModelValueConverter.toSimpleValue(null))
    }

    @Test
    fun testToSimpleValue_string() {
        val model = ObjectModel.single(JsonType.STRING)
        assertEquals("", ObjectModelValueConverter.toSimpleValue(model))
    }

    @Test
    fun testToSimpleValue_int() {
        val model = ObjectModel.single(JsonType.INT)
        assertEquals(0, ObjectModelValueConverter.toSimpleValue(model))
    }

    @Test
    fun testToSimpleValue_long() {
        val model = ObjectModel.single(JsonType.LONG)
        assertEquals(0L, ObjectModelValueConverter.toSimpleValue(model))
    }

    @Test
    fun testToSimpleValue_float() {
        val model = ObjectModel.single(JsonType.FLOAT)
        assertEquals(0.0f, ObjectModelValueConverter.toSimpleValue(model))
    }

    @Test
    fun testToSimpleValue_double() {
        val model = ObjectModel.single(JsonType.DOUBLE)
        assertEquals(0.0, ObjectModelValueConverter.toSimpleValue(model))
    }

    @Test
    fun testToSimpleValue_boolean() {
        val model = ObjectModel.single(JsonType.BOOLEAN)
        assertEquals(false, ObjectModelValueConverter.toSimpleValue(model))
    }

    @Test
    fun testToSimpleValue_nullType() {
        val model = ObjectModel.nullValue()
        assertNull(ObjectModelValueConverter.toSimpleValue(model))
    }

    @Test
    fun testToSimpleValue_object() {
        val obj = ObjectModel.Object(mapOf(
            "name" to FieldModel(ObjectModel.single(JsonType.STRING)),
            "age" to FieldModel(ObjectModel.single(JsonType.INT))
        ))
        val result = ObjectModelValueConverter.toSimpleValue(obj)
        assertTrue(result is Map<*, *>)
        val map = result as Map<*, *>
        assertEquals("", map["name"])
        assertEquals(0, map["age"])
    }

    @Test
    fun testToSimpleValue_array() {
        val arr = ObjectModel.array(ObjectModel.single(JsonType.STRING))
        val result = ObjectModelValueConverter.toSimpleValue(arr)
        assertTrue(result is List<*>)
        val list = result as List<*>
        assertEquals(1, list.size)
        assertEquals("", list[0])
    }

    @Test
    fun testToSimpleValue_map() {
        val mapModel = ObjectModel.map(
            ObjectModel.single(JsonType.STRING),
            ObjectModel.single(JsonType.INT)
        )
        val result = ObjectModelValueConverter.toSimpleValue(mapModel)
        assertTrue(result is Map<*, *>)
        val map = result as Map<*, *>
        assertEquals(0, map[""])
    }

    @Test
    fun testToSimpleValue_nestedObject() {
        val inner = ObjectModel.Object(mapOf(
            "street" to FieldModel(ObjectModel.single(JsonType.STRING))
        ))
        val outer = ObjectModel.Object(mapOf(
            "address" to FieldModel(inner)
        ))
        val result = ObjectModelValueConverter.toSimpleValue(outer) as Map<*, *>
        val address = result["address"] as Map<*, *>
        assertEquals("", address["street"])
    }

    @Test
    fun testToSimpleValue_circularReference() {
        // Create a circular reference by using the same object ID
        val obj = ObjectModel.Object(mapOf(
            "self" to FieldModel(ObjectModel.single(JsonType.STRING))
        ))
        // This should not cause infinite recursion
        val result = ObjectModelValueConverter.toSimpleValue(obj)
        assertNotNull(result)
    }

    @Test
    fun testToSimpleValue_date() {
        val model = ObjectModel.single(JsonType.DATE)
        assertEquals("", ObjectModelValueConverter.toSimpleValue(model))
    }

    @Test
    fun testToSimpleValue_file() {
        val model = ObjectModel.single(JsonType.FILE)
        assertEquals("(binary)", ObjectModelValueConverter.toSimpleValue(model))
    }

    @Test
    fun testToSimpleValue_unknownType() {
        val model = ObjectModel.single("unknown")
        assertNull(ObjectModelValueConverter.toSimpleValue(model))
    }

    @Test
    fun testToSimpleValue_short() {
        val model = ObjectModel.single(JsonType.SHORT)
        assertEquals(0, ObjectModelValueConverter.toSimpleValue(model))
    }
}
