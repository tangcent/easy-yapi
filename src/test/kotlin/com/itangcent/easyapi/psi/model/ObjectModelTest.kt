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

    /**
     * Tests that flattenFields handles circular object references without
     * producing an excessively large result.
     *
     * This simulates the scenario from https://github.com/tangcent/easy-yapi/issues/1325
     * where a DTO has a field referencing itself (e.g., parentChannel: ChannelDTO).
     * The cache in DefaultPsiClassHelper can produce ObjectModel instances with
     * actual circular references (same Object instance in the tree).
     *
     * Without circular reference detection, flattenFields would expand
     * N^maxDepth entries (e.g., 10 fields × depth 5 = 100,000 entries).
     */
    @Test
    fun testObject_flattenFields_circularReference() {
        // Create a circular ObjectModel: obj.fields["self"].model === obj
        val fields = linkedMapOf<String, FieldModel>()
        val obj = ObjectModel.Object(fields)
        fields["name"] = FieldModel(ObjectModel.single("string"))
        fields["self"] = FieldModel(obj) // circular reference!

        val flat = obj.flattenFields(maxDepth = 5)
        assertNotNull(flat)
        // Should contain "name" at various depths but not explode
        assertTrue("Should contain top-level 'name'", flat.containsKey("name"))
        // The total number of entries should be bounded, not exponential
        assertTrue(
            "flattenFields with circular reference should not produce excessive entries (got ${flat.size})",
            flat.size < 100
        )
    }

    /**
     * Tests flattenFields with a mutual circular reference (A → B → A).
     * This is the more realistic scenario: ChannelDTO has SubChannelDTO,
     * and SubChannelDTO has ChannelDTO.
     */
    @Test
    fun testObject_flattenFields_mutualCircularReference() {
        val fieldsA = linkedMapOf<String, FieldModel>()
        val fieldsB = linkedMapOf<String, FieldModel>()
        val objA = ObjectModel.Object(fieldsA)
        val objB = ObjectModel.Object(fieldsB)

        fieldsA["id"] = FieldModel(ObjectModel.single("long"))
        fieldsA["name"] = FieldModel(ObjectModel.single("string"))
        fieldsA["child"] = FieldModel(objB)

        fieldsB["childId"] = FieldModel(ObjectModel.single("long"))
        fieldsB["parent"] = FieldModel(objA) // circular back to A

        val flat = objA.flattenFields(maxDepth = 5)
        assertNotNull(flat)
        assertTrue("Should contain 'id'", flat.containsKey("id"))
        assertTrue("Should contain 'name'", flat.containsKey("name"))
        assertTrue(
            "flattenFields with mutual circular reference should not produce excessive entries (got ${flat.size})",
            flat.size < 200
        )
    }

    /**
     * Tests flattenFields with a wide class (many fields) and circular reference.
     * This is the worst case for the OOM bug: N fields × maxDepth levels = N^maxDepth entries.
     */
    @Test
    fun testObject_flattenFields_wideCircularReference() {
        val fields = linkedMapOf<String, FieldModel>()
        val obj = ObjectModel.Object(fields)

        // Add 10 simple fields
        for (i in 1..10) {
            fields["field$i"] = FieldModel(ObjectModel.single("string"))
        }
        // Add circular reference
        fields["self"] = FieldModel(obj)

        val flat = obj.flattenFields(maxDepth = 5)
        assertNotNull(flat)
        // With single self-reference, this is linear: 10 fields × 5 levels = 50
        println("Wide circular reference flattenFields produced ${flat.size} entries")
        assertTrue(
            "Wide class with circular reference should not produce excessive entries (got ${flat.size})",
            flat.size < 500
        )
    }

    /**
     * Tests flattenFields with multiple fields referencing the same complex type
     * that references back. This creates exponential growth: at each level,
     * N complex fields each expand back to the parent, creating N^depth entries.
     *
     * Example: ChannelDTO has 5 SubChannelDTO fields, each SubChannelDTO has
     * a ChannelDTO backRef. This creates 5^depth growth.
     */
    @Test
    fun testObject_flattenFields_multipleBackReferences() {
        val fieldsA = linkedMapOf<String, FieldModel>()
        val fieldsB = linkedMapOf<String, FieldModel>()
        val objA = ObjectModel.Object(fieldsA)
        val objB = ObjectModel.Object(fieldsB)

        // A has 5 fields of type B
        fieldsA["name"] = FieldModel(ObjectModel.single("string"))
        for (i in 1..5) {
            fieldsA["ref$i"] = FieldModel(objB)
        }

        // B has a back-reference to A
        fieldsB["value"] = FieldModel(ObjectModel.single("string"))
        fieldsB["parent"] = FieldModel(objA)

        val flat = objA.flattenFields(maxDepth = 5)
        assertNotNull(flat)
        println("Multiple back-references flattenFields produced ${flat.size} entries")
        // With 5 refs to B, each B refs back to A: growth is 5^(depth/2)
        // At maxDepth=5: roughly 5^2 * some_factor ≈ hundreds of entries
        // This should still be manageable, but with more fields it could explode
        assertTrue(
            "Multiple back-references should not produce excessive entries (got ${flat.size})",
            flat.size < 5000
        )
    }

    /**
     * Stress test: simulates a real-world DTO with many fields and multiple
     * circular reference paths. This is the scenario from issue #1325 where
     * ChannelDTO with ~20 fields and circular references caused OOM.
     */
    @Test
    fun testObject_flattenFields_stressTest() {
        val fieldsChannel = linkedMapOf<String, FieldModel>()
        val fieldsSub = linkedMapOf<String, FieldModel>()
        val channelObj = ObjectModel.Object(fieldsChannel)
        val subObj = ObjectModel.Object(fieldsSub)

        // ChannelDTO-like: 15 simple fields + self-ref + list of subs
        for (i in 1..15) {
            fieldsChannel["field$i"] = FieldModel(ObjectModel.single("string"))
        }
        fieldsChannel["parentChannel"] = FieldModel(channelObj) // self-reference
        fieldsChannel["subChannel1"] = FieldModel(subObj)
        fieldsChannel["subChannel2"] = FieldModel(subObj)
        fieldsChannel["subChannel3"] = FieldModel(subObj)

        // SubChannelDTO: 5 simple fields + back-ref to channel
        for (i in 1..5) {
            fieldsSub["subField$i"] = FieldModel(ObjectModel.single("string"))
        }
        fieldsSub["ownerChannel"] = FieldModel(channelObj) // back-reference

        val flat = channelObj.flattenFields(maxDepth = 5)
        assertNotNull(flat)
        println("Stress test flattenFields produced ${flat.size} entries")
        assertTrue(
            "Stress test should not produce excessive entries (got ${flat.size})",
            flat.size < 100000
        )
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
