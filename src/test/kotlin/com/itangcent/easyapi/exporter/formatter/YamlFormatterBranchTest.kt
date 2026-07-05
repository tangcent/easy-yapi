package com.itangcent.easyapi.exporter.formatter

import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.JsonType
import org.junit.Assert.*
import org.junit.Test

/**
 * Comprehensive unit tests for [YamlFormatter].
 *
 * Covers all branches of the formatter:
 * - Top-level rendering of Object, Array, Map, Single
 * - Empty objects and cycle detection (MAX_VISITS)
 * - Nested objects, arrays, maps
 * - Arrays of objects, arrays, maps, singles
 * - Maps with various value types
 * - Default value handling (string, number, boolean, null, custom)
 */
class YamlFormatterBranchTest {

    // ---------- Top-level rendering ----------

    @Test
    fun testFormatTopLevelSingle() {
        val model = ObjectModel.Single(JsonType.STRING)
        val result = YamlFormatter.format(model)
        assertEquals("value: \"\"", result)
    }

    @Test
    fun testFormatTopLevelSingleWithUnknownType() {
        val model = ObjectModel.Single("unknownType")
        val result = YamlFormatter.format(model)
        assertEquals("value: null", result)
    }

    @Test
    fun testFormatTopLevelEmptyObject() {
        val model = ObjectModel.Object(emptyMap())
        val result = YamlFormatter.format(model)
        assertEquals("{}", result)
    }

    @Test
    fun testFormatTopLevelArray() {
        val model = ObjectModel.Array(ObjectModel.Single(JsonType.STRING))
        val result = YamlFormatter.format(model)
        assertEquals("- \"\"", result)
    }

    @Test
    fun testFormatTopLevelMap() {
        val model = ObjectModel.MapModel(
            ObjectModel.Single(JsonType.STRING),
            ObjectModel.Single(JsonType.INT)
        )
        val result = YamlFormatter.format(model)
        assertEquals("key: \"\"\nvalue: 0", result)
    }

    // ---------- Object field rendering ----------

    @Test
    fun testFormatObjectWithScalars() {
        val obj = ObjectModel.Object(
            mapOf(
                "name" to FieldModel(ObjectModel.Single(JsonType.STRING)),
                "age" to FieldModel(ObjectModel.Single(JsonType.INT)),
                "active" to FieldModel(ObjectModel.Single(JsonType.BOOLEAN))
            )
        )
        val result = YamlFormatter.format(obj)
        assertEquals("name: \"\"\nage: 0\nactive: false", result)
    }

    @Test
    fun testFormatObjectWithLongAndDouble() {
        val obj = ObjectModel.Object(
            mapOf(
                "big" to FieldModel(ObjectModel.Single(JsonType.LONG)),
                "price" to FieldModel(ObjectModel.Single(JsonType.DOUBLE)),
                "rate" to FieldModel(ObjectModel.Single(JsonType.FLOAT))
            )
        )
        val result = YamlFormatter.format(obj)
        assertEquals("big: 0\nprice: 0.0\nrate: 0.0", result)
    }

    @Test
    fun testFormatObjectWithShortType() {
        val obj = ObjectModel.Object(
            mapOf(
                "s" to FieldModel(ObjectModel.Single(JsonType.SHORT))
            )
        )
        val result = YamlFormatter.format(obj)
        assertEquals("s: 0", result)
    }

    @Test
    fun testFormatObjectWithUnknownType() {
        val obj = ObjectModel.Object(
            mapOf(
                "mystery" to FieldModel(ObjectModel.Single("unknown"))
            )
        )
        val result = YamlFormatter.format(obj)
        assertEquals("mystery: null", result)
    }

    // ---------- Default value handling ----------

    @Test
    fun testFormatWithFieldDefaultValueString() {
        val obj = ObjectModel.Object(
            mapOf(
                "name" to FieldModel(
                    model = ObjectModel.Single(JsonType.STRING),
                    defaultValue = "alice"
                )
            )
        )
        val result = YamlFormatter.format(obj)
        assertEquals("name: alice", result)
    }

    @Test
    fun testFormatWithFieldDefaultValueEmptyString() {
        val obj = ObjectModel.Object(
            mapOf(
                "name" to FieldModel(
                    model = ObjectModel.Single(JsonType.STRING),
                    defaultValue = ""
                )
            )
        )
        val result = YamlFormatter.format(obj)
        assertEquals("name: \"\"", result)
    }

    @Test
    fun testFormatWithFieldDefaultValueNumber() {
        val obj = ObjectModel.Object(
            mapOf(
                "age" to FieldModel(
                    model = ObjectModel.Single(JsonType.INT),
                    defaultValue = "42"
                )
            )
        )
        val result = YamlFormatter.format(obj)
        // defaultValue is a String here, so it's treated as a string
        assertEquals("age: 42", result)
    }

    @Test
    fun testFormatWithFieldDefaultValueBoolean() {
        val obj = ObjectModel.Object(
            mapOf(
                "active" to FieldModel(
                    model = ObjectModel.Single(JsonType.BOOLEAN),
                    defaultValue = "true"
                )
            )
        )
        val result = YamlFormatter.format(obj)
        assertEquals("active: true", result)
    }

    @Test
    fun testFormatWithFieldDefaultValueNullForUnknownType() {
        val obj = ObjectModel.Object(
            mapOf(
                "data" to FieldModel(
                    model = ObjectModel.Single("unknown"),
                    defaultValue = null
                )
            )
        )
        val result = YamlFormatter.format(obj)
        assertEquals("data: null", result)
    }

    // ---------- Nested objects ----------

    @Test
    fun testFormatNestedObject() {
        val inner = ObjectModel.Object(
            mapOf("id" to FieldModel(ObjectModel.Single(JsonType.INT)))
        )
        val outer = ObjectModel.Object(
            mapOf("nested" to FieldModel(inner))
        )
        val result = YamlFormatter.format(outer)
        assertEquals("nested:\n\n  id: 0", result)
    }

    @Test
    fun testFormatNestedEmptyObject() {
        val inner = ObjectModel.Object(emptyMap())
        val outer = ObjectModel.Object(
            mapOf("nested" to FieldModel(inner))
        )
        val result = YamlFormatter.format(outer)
        assertEquals("nested: {}", result)
    }

    @Test
    fun testFormatDeeplyNestedObject() {
        val leaf = ObjectModel.Object(
            mapOf("value" to FieldModel(ObjectModel.Single(JsonType.STRING)))
        )
        val middle = ObjectModel.Object(
            mapOf("child" to FieldModel(leaf))
        )
        val root = ObjectModel.Object(
            mapOf("top" to FieldModel(middle))
        )
        val result = YamlFormatter.format(root)
        assertEquals("top:\n\n  child:\n\n    value: \"\"", result)
    }

    // ---------- Array rendering ----------

    @Test
    fun testFormatArrayInObject() {
        val array = ObjectModel.Array(ObjectModel.Single(JsonType.STRING))
        val obj = ObjectModel.Object(
            mapOf("tags" to FieldModel(array))
        )
        val result = YamlFormatter.format(obj)
        assertEquals("tags:\n  - \"\"", result)
    }

    @Test
    fun testFormatArrayOfObjects() {
        val item = ObjectModel.Object(
            mapOf("id" to FieldModel(ObjectModel.Single(JsonType.INT)))
        )
        val array = ObjectModel.Array(item)
        val obj = ObjectModel.Object(
            mapOf("items" to FieldModel(array))
        )
        val result = YamlFormatter.format(obj)
        assertEquals("items:\n  - id: 0", result)
    }

    @Test
    fun testFormatArrayOfObjectsWithMultipleFields() {
        val item = ObjectModel.Object(
            mapOf(
                "id" to FieldModel(ObjectModel.Single(JsonType.INT)),
                "name" to FieldModel(ObjectModel.Single(JsonType.STRING))
            )
        )
        val array = ObjectModel.Array(item)
        val obj = ObjectModel.Object(
            mapOf("items" to FieldModel(array))
        )
        val result = YamlFormatter.format(obj)
        assertEquals("items:\n  - id: 0\n    name: \"\"", result)
    }

    @Test
    fun testFormatArrayOfEmptyObjects() {
        val item = ObjectModel.Object(emptyMap())
        val array = ObjectModel.Array(item)
        val obj = ObjectModel.Object(
            mapOf("items" to FieldModel(array))
        )
        val result = YamlFormatter.format(obj)
        assertEquals("items:\n  - {}", result)
    }

    @Test
    fun testFormatArrayOfArrays() {
        val innerArray = ObjectModel.Array(ObjectModel.Single(JsonType.INT))
        val outerArray = ObjectModel.Array(innerArray)
        val obj = ObjectModel.Object(
            mapOf("matrix" to FieldModel(outerArray))
        )
        val result = YamlFormatter.format(obj)
        // Outer: "  - " (indent 1), inner: "    - " (indent 2) → "  -     - 0"
        assertEquals("matrix:\n  -     - 0", result)
    }

    @Test
    fun testFormatArrayOfMaps() {
        val map = ObjectModel.MapModel(
            ObjectModel.Single(JsonType.STRING),
            ObjectModel.Single(JsonType.INT)
        )
        val array = ObjectModel.Array(map)
        val obj = ObjectModel.Object(
            mapOf("entries" to FieldModel(array))
        )
        val result = YamlFormatter.format(obj)
        assertEquals("entries:\n  - \n    key: \"\"\n    value: 0", result)
    }

    @Test
    fun testFormatTopLevelArrayOfObjects() {
        val item = ObjectModel.Object(
            mapOf("id" to FieldModel(ObjectModel.Single(JsonType.INT)))
        )
        val array = ObjectModel.Array(item)
        val result = YamlFormatter.format(array)
        assertEquals("- id: 0", result)
    }

    @Test
    fun testFormatTopLevelArrayOfArrays() {
        val innerArray = ObjectModel.Array(ObjectModel.Single(JsonType.INT))
        val outerArray = ObjectModel.Array(innerArray)
        val result = YamlFormatter.format(outerArray)
        assertEquals("-   - 0", result)
    }

    @Test
    fun testFormatTopLevelArrayOfMaps() {
        val map = ObjectModel.MapModel(
            ObjectModel.Single(JsonType.STRING),
            ObjectModel.Single(JsonType.INT)
        )
        val array = ObjectModel.Array(map)
        val result = YamlFormatter.format(array)
        assertEquals("- \n  key: \"\"\n  value: 0", result)
    }

    @Test
    fun testFormatTopLevelArrayOfEmptyObjects() {
        val item = ObjectModel.Object(emptyMap())
        val array = ObjectModel.Array(item)
        val result = YamlFormatter.format(array)
        assertEquals("- {}", result)
    }

    // ---------- Map rendering ----------

    @Test
    fun testFormatMapInObject() {
        val map = ObjectModel.MapModel(
            ObjectModel.Single(JsonType.STRING),
            ObjectModel.Single(JsonType.INT)
        )
        val obj = ObjectModel.Object(
            mapOf("metadata" to FieldModel(map))
        )
        val result = YamlFormatter.format(obj)
        assertEquals("metadata:\n  key: \"\"\n  value: 0", result)
    }

    @Test
    fun testFormatMapWithObjectValue() {
        val valueObj = ObjectModel.Object(
            mapOf("id" to FieldModel(ObjectModel.Single(JsonType.INT)))
        )
        val map = ObjectModel.MapModel(
            ObjectModel.Single(JsonType.STRING),
            valueObj
        )
        val obj = ObjectModel.Object(
            mapOf("data" to FieldModel(map))
        )
        val result = YamlFormatter.format(obj)
        assertEquals("data:\n  key: \"\"\n  value:\n\n    id: 0", result)
    }

    @Test
    fun testFormatMapWithEmptyObjectValue() {
        val valueObj = ObjectModel.Object(emptyMap())
        val map = ObjectModel.MapModel(
            ObjectModel.Single(JsonType.STRING),
            valueObj
        )
        val obj = ObjectModel.Object(
            mapOf("data" to FieldModel(map))
        )
        val result = YamlFormatter.format(obj)
        assertEquals("data:\n  key: \"\"\n  value: {}", result)
    }

    @Test
    fun testFormatMapWithArrayValue() {
        val array = ObjectModel.Array(ObjectModel.Single(JsonType.INT))
        val map = ObjectModel.MapModel(
            ObjectModel.Single(JsonType.STRING),
            array
        )
        val obj = ObjectModel.Object(
            mapOf("data" to FieldModel(map))
        )
        val result = YamlFormatter.format(obj)
        assertEquals("data:\n  key: \"\"\n  value:\n    - 0", result)
    }

    @Test
    fun testFormatMapWithMapValue() {
        val innerMap = ObjectModel.MapModel(
            ObjectModel.Single(JsonType.STRING),
            ObjectModel.Single(JsonType.INT)
        )
        val outerMap = ObjectModel.MapModel(
            ObjectModel.Single(JsonType.STRING),
            innerMap
        )
        val obj = ObjectModel.Object(
            mapOf("data" to FieldModel(outerMap))
        )
        val result = YamlFormatter.format(obj)
        assertEquals("data:\n  key: \"\"\n  value:\n    key: \"\"\n    value: 0", result)
    }

    @Test
    fun testFormatTopLevelMapWithObjectValue() {
        val valueObj = ObjectModel.Object(
            mapOf("id" to FieldModel(ObjectModel.Single(JsonType.INT)))
        )
        val map = ObjectModel.MapModel(
            ObjectModel.Single(JsonType.STRING),
            valueObj
        )
        val result = YamlFormatter.format(map)
        assertEquals("key: \"\"\nvalue:\n\n  id: 0", result)
    }

    @Test
    fun testFormatTopLevelMapWithArrayValue() {
        val array = ObjectModel.Array(ObjectModel.Single(JsonType.INT))
        val map = ObjectModel.MapModel(
            ObjectModel.Single(JsonType.STRING),
            array
        )
        val result = YamlFormatter.format(map)
        assertEquals("key: \"\"\nvalue:\n  - 0", result)
    }

    @Test
    fun testFormatTopLevelMapWithMapValue() {
        val innerMap = ObjectModel.MapModel(
            ObjectModel.Single(JsonType.STRING),
            ObjectModel.Single(JsonType.INT)
        )
        val outerMap = ObjectModel.MapModel(
            ObjectModel.Single(JsonType.STRING),
            innerMap
        )
        val result = YamlFormatter.format(outerMap)
        assertEquals("key: \"\"\nvalue:\n  key: \"\"\n  value: 0", result)
    }

    @Test
    fun testFormatMapWithEmptyObjectKey() {
        val map = ObjectModel.MapModel(
            ObjectModel.Object(emptyMap()),
            ObjectModel.Single(JsonType.INT)
        )
        val result = YamlFormatter.format(map)
        assertEquals("key: {}\nvalue: 0", result)
    }

    // ---------- Cycle detection ----------

    @Test
    fun testFormatSelfReferencingObject() {
        // Create a self-referencing object: { parent: <self> }
        val obj = ObjectModel.Object(mutableMapOf<String, FieldModel>())
        (obj.fields as MutableMap)["parent"] = FieldModel(obj)
        val result = YamlFormatter.format(obj)
        // First visit renders parent field, second visit renders {} due to MAX_VISITS
        assertTrue("Should contain parent field", result.contains("parent:"))
        assertTrue("Should render cycle as {}", result.contains("{}"))
    }

    @Test
    fun testFormatMutualReferences() {
        // a -> b -> a (cycle)
        val a = ObjectModel.Object(mutableMapOf<String, FieldModel>())
        val b = ObjectModel.Object(mutableMapOf<String, FieldModel>())
        (a.fields as MutableMap)["ref"] = FieldModel(b)
        (b.fields as MutableMap)["back"] = FieldModel(a)
        val result = YamlFormatter.format(a)
        // Should not infinite loop; should contain {} for cycle
        assertTrue("Should not hang and should contain {}", result.contains("{}"))
    }

    @Test
    fun testFormatCycleInArrayItem() {
        // Object with a field that is an array of itself
        val obj = ObjectModel.Object(mutableMapOf<String, FieldModel>())
        val array = ObjectModel.Array(obj)
        (obj.fields as MutableMap)["children"] = FieldModel(array)
        val result = YamlFormatter.format(obj)
        // Should not infinite loop
        assertTrue("Should not hang", result.contains("children"))
    }

    @Test
    fun testFormatCycleInMapValue() {
        // Object with a map whose value is the object itself
        val obj = ObjectModel.Object(mutableMapOf<String, FieldModel>())
        val map = ObjectModel.MapModel(ObjectModel.Single(JsonType.STRING), obj)
        (obj.fields as MutableMap)["self"] = FieldModel(map)
        val result = YamlFormatter.format(obj)
        // Should not infinite loop
        assertTrue("Should not hang", result.contains("self"))
    }

    // ---------- Mixed complex structures ----------

    @Test
    fun testFormatComplexNestedStructure() {
        val item = ObjectModel.Object(
            mapOf(
                "id" to FieldModel(ObjectModel.Single(JsonType.INT)),
                "name" to FieldModel(ObjectModel.Single(JsonType.STRING)),
                "tags" to FieldModel(ObjectModel.Array(ObjectModel.Single(JsonType.STRING)))
            )
        )
        val obj = ObjectModel.Object(
            mapOf(
                "items" to FieldModel(ObjectModel.Array(item)),
                "count" to FieldModel(ObjectModel.Single(JsonType.INT))
            )
        )
        val result = YamlFormatter.format(obj)
        val expected = "items:\n  - id: 0\n    name: \"\"\n    tags:\n      - \"\"\ncount: 0"
        assertEquals(expected, result)
    }

    @Test
    fun testFormatObjectWithMultipleFieldsAndNestedArray() {
        val user = ObjectModel.Object(
            mapOf(
                "id" to FieldModel(ObjectModel.Single(JsonType.LONG)),
                "name" to FieldModel(ObjectModel.Single(JsonType.STRING)),
                "active" to FieldModel(ObjectModel.Single(JsonType.BOOLEAN))
            )
        )
        val obj = ObjectModel.Object(
            mapOf(
                "user" to FieldModel(user),
                "tags" to FieldModel(ObjectModel.Array(ObjectModel.Single(JsonType.STRING)))
            )
        )
        val result = YamlFormatter.format(obj)
        val expected = "user:\n\n  id: 0\n  name: \"\"\n  active: false\ntags:\n  - \"\""
        assertEquals(expected, result)
    }

    // ---------- Edge cases ----------

    @Test
    fun testFormatEmptyObjectInArray() {
        val array = ObjectModel.Array(ObjectModel.Object(emptyMap()))
        val result = YamlFormatter.format(array)
        assertEquals("- {}", result)
    }

    @Test
    fun testFormatObjectWithEmptyArrayField() {
        // Array of singles (default value)
        val obj = ObjectModel.Object(
            mapOf(
                "tags" to FieldModel(ObjectModel.Array(ObjectModel.Single(JsonType.STRING)))
            )
        )
        val result = YamlFormatter.format(obj)
        assertEquals("tags:\n  - \"\"", result)
    }

    @Test
    fun testFormatTopLevelSingleWithInt() {
        val result = YamlFormatter.format(ObjectModel.Single(JsonType.INT))
        assertEquals("value: 0", result)
    }

    @Test
    fun testFormatTopLevelSingleWithBoolean() {
        val result = YamlFormatter.format(ObjectModel.Single(JsonType.BOOLEAN))
        assertEquals("value: false", result)
    }

    @Test
    fun testFormatTopLevelSingleWithLong() {
        val result = YamlFormatter.format(ObjectModel.Single(JsonType.LONG))
        assertEquals("value: 0", result)
    }

    @Test
    fun testFormatTopLevelSingleWithDouble() {
        val result = YamlFormatter.format(ObjectModel.Single(JsonType.DOUBLE))
        assertEquals("value: 0.0", result)
    }

    @Test
    fun testFormatTopLevelSingleWithFloat() {
        val result = YamlFormatter.format(ObjectModel.Single(JsonType.FLOAT))
        assertEquals("value: 0.0", result)
    }

    // ---------- Prefix (@ConfigurationProperties) ----------

    @Test
    fun testFormatWithEmptyPrefixEqualsNoPrefix() {
        val obj = ObjectModel.Object(
            mapOf(
                "id" to FieldModel(ObjectModel.Single(JsonType.INT)),
                "name" to FieldModel(ObjectModel.Single(JsonType.STRING))
            )
        )
        val withoutPrefix = YamlFormatter.format(obj)
        val withEmptyPrefix = YamlFormatter.format(obj, prefix = "")
        assertEquals(withoutPrefix, withEmptyPrefix)
    }

    @Test
    fun testFormatWithSingleSegmentPrefixForObject() {
        val obj = ObjectModel.Object(
            mapOf(
                "id" to FieldModel(ObjectModel.Single(JsonType.INT)),
                "name" to FieldModel(ObjectModel.Single(JsonType.STRING))
            )
        )
        val result = YamlFormatter.format(obj, prefix = "app")
        assertEquals("app:\n\n  id: 0\n  name: \"\"", result)
    }

    @Test
    fun testFormatWithMultiSegmentPrefixForObject() {
        val obj = ObjectModel.Object(
            mapOf(
                "id" to FieldModel(ObjectModel.Single(JsonType.INT)),
                "name" to FieldModel(ObjectModel.Single(JsonType.STRING))
            )
        )
        val result = YamlFormatter.format(obj, prefix = "app.config")
        assertEquals("app:\n\n  config:\n\n    id: 0\n    name: \"\"", result)
    }

    @Test
    fun testFormatWithPrefixCollapsesEmptySegments() {
        val obj = ObjectModel.Object(
            mapOf("id" to FieldModel(ObjectModel.Single(JsonType.INT)))
        )
        // Leading/trailing/repeated dots collapse to the single segment "app"
        val result = YamlFormatter.format(obj, prefix = ".app.")
        assertEquals("app:\n\n  id: 0", result)
    }

    @Test
    fun testFormatWithPrefixAndNestedObject() {
        val inner = ObjectModel.Object(
            mapOf("id" to FieldModel(ObjectModel.Single(JsonType.INT)))
        )
        val obj = ObjectModel.Object(
            mapOf("nested" to FieldModel(inner))
        )
        val result = YamlFormatter.format(obj, prefix = "app")
        assertEquals("app:\n\n  nested:\n\n    id: 0", result)
    }

    @Test
    fun testFormatWithPrefixAndArrayField() {
        val array = ObjectModel.Array(ObjectModel.Single(JsonType.STRING))
        val obj = ObjectModel.Object(
            mapOf("tags" to FieldModel(array))
        )
        val result = YamlFormatter.format(obj, prefix = "app")
        assertEquals("app:\n\n  tags:\n    - \"\"", result)
    }

    @Test
    fun testFormatWithPrefixAndEmptyObject() {
        val obj = ObjectModel.Object(emptyMap())
        val result = YamlFormatter.format(obj, prefix = "app")
        assertEquals("app: {}", result)
    }

    @Test
    fun testFormatTopLevelArrayWithPrefix() {
        val item = ObjectModel.Object(
            mapOf("id" to FieldModel(ObjectModel.Single(JsonType.INT)))
        )
        val array = ObjectModel.Array(item)
        val result = YamlFormatter.format(array, prefix = "app")
        assertEquals("app:\n  - id: 0", result)
    }

    @Test
    fun testFormatTopLevelMapWithPrefix() {
        val map = ObjectModel.MapModel(
            ObjectModel.Single(JsonType.STRING),
            ObjectModel.Single(JsonType.INT)
        )
        val result = YamlFormatter.format(map, prefix = "app")
        // Map follows the same convention as a map field value (no blank line
        // between the key and its map body — see testFormatMapInObject).
        assertEquals("app:\n  key: \"\"\n  value: 0", result)
    }

    @Test
    fun testFormatTopLevelSingleWithPrefix() {
        val single = ObjectModel.Single(JsonType.STRING)
        val result = YamlFormatter.format(single, prefix = "app")
        assertEquals("app:\n  value: \"\"", result)
    }
}
