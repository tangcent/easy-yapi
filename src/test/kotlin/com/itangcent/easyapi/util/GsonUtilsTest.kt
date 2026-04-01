package com.itangcent.easyapi.util

import org.junit.Assert.*
import org.junit.Test

class GsonUtilsTest {

    @Test
    fun testToJson_withPrimitive() {
        assertEquals("123", GsonUtils.toJson(123))
        assertEquals("true", GsonUtils.toJson(true))
        assertEquals("false", GsonUtils.toJson(false))
        assertEquals("12.34", GsonUtils.toJson(12.34))
    }

    @Test
    fun testToJson_withString() {
        assertEquals("\"hello\"", GsonUtils.toJson("hello"))
        assertEquals("\"\"", GsonUtils.toJson(""))
    }

    @Test
    fun testToJson_withNull() {
        assertEquals("null", GsonUtils.toJson(null))
    }

    @Test
    fun testToJson_withMap() {
        val map = linkedMapOf(
            "name" to "test",
            "value" to 123
        )
        assertEquals("{\"name\":\"test\",\"value\":123}", GsonUtils.toJson(map))
    }

    @Test
    fun testToJson_withList() {
        val list = listOf(1, 2, 3)
        assertEquals("[1,2,3]", GsonUtils.toJson(list))
    }

    @Test
    fun testToJson_withObject() {
        val obj = TestPoint(1, 2)
        assertEquals("{\"x\":1,\"y\":2}", GsonUtils.toJson(obj))
    }

    @Test
    fun testToJson_withNestedObject() {
        val nested = mapOf(
            "outer" to mapOf(
                "inner" to "value"
            )
        )
        assertEquals("{\"outer\":{\"inner\":\"value\"}}", GsonUtils.toJson(nested))
    }

    @Test
    fun testPrettyJson_withObject() {
        val obj = TestPoint(1, 2)
        val expected = "{\n  \"x\": 1,\n  \"y\": 2\n}"
        assertEquals(expected, GsonUtils.prettyJson(obj))
    }

    @Test
    fun testPrettyJson_withMap() {
        val map = linkedMapOf("key" to "value")
        val expected = "{\n  \"key\": \"value\"\n}"
        assertEquals(expected, GsonUtils.prettyJson(map))
    }

    @Test
    fun testPrettyJson_withNull() {
        assertEquals("null", GsonUtils.prettyJson(null))
    }

    @Test
    fun testPrettyJson_withList() {
        val list = listOf(1, 2, 3)
        val expected = "[\n  1,\n  2,\n  3\n]"
        assertEquals(expected, GsonUtils.prettyJson(list))
    }

    @Test
    fun testFromJson_toObject() {
        val json = "{\"x\":1,\"y\":2}"
        val result: TestPoint = GsonUtils.fromJson(json)
        assertEquals(TestPoint(1, 2), result)
    }

    @Test
    fun testFromJson_toMap() {
        val json = "{\"name\":\"test\",\"value\":123}"
        val result: Map<String, Any> = GsonUtils.fromJson(json)
        assertEquals("test", result["name"])
        assertEquals(123.0, result["value"])
    }

    @Test
    fun testFromJson_toList() {
        val json = "[1,2,3]"
        val result: List<Int> = GsonUtils.fromJson(json)
        assertEquals(listOf(1, 2, 3), result)
    }

    @Test
    fun testFromJson_toString() {
        val json = "\"hello\""
        val result: String = GsonUtils.fromJson(json)
        assertEquals("hello", result)
    }

    @Test
    fun testFromJson_toInt() {
        val json = "123"
        val result: Int = GsonUtils.fromJson(json)
        assertEquals(123, result)
    }

    @Test
    fun testFromJson_toBoolean() {
        assertEquals(true, GsonUtils.fromJson<Boolean>("true"))
        assertEquals(false, GsonUtils.fromJson<Boolean>("false"))
    }

    @Test
    fun testFromJson_withType() {
        val json = "{\"x\":10,\"y\":20}"
        val result: TestPoint = GsonUtils.fromJson(json, TestPoint::class.java)
        assertEquals(TestPoint(10, 20), result)
    }

    @Test
    fun testFromJson_withNestedGeneric() {
        val json = "{\"data\":{\"x\":1,\"y\":2}}"
        val result: Map<String, Any> = GsonUtils.fromJson(json)
        val data = result["data"] as Map<*, *>
        assertEquals(1.0, data["x"])
        assertEquals(2.0, data["y"])
    }

    @Test
    fun testRoundTrip() {
        val original = TestPoint(42, 100)
        val json = GsonUtils.toJson(original)
        val restored: TestPoint = GsonUtils.fromJson(json)
        assertEquals(original, restored)
    }

    @Test
    fun testRoundTrip_withMap() {
        val original = linkedMapOf(
            "key1" to "value1",
            "key2" to 123,
            "key3" to true
        )
        val json = GsonUtils.toJson(original)
        val restored: Map<String, Any> = GsonUtils.fromJson(json)
        assertEquals("value1", restored["key1"])
        assertEquals(123.0, restored["key2"])
        assertEquals(true, restored["key3"])
    }

    @Test
    fun testGsonInstance() {
        assertNotNull(GsonUtils.GSON)
        val json = GsonUtils.GSON.toJson(mapOf("a" to 1))
        assertEquals("{\"a\":1}", json)
    }

    @Test
    fun testPrettyInstance() {
        assertNotNull(GsonUtils.PRETTY)
        val json = GsonUtils.PRETTY.toJson(mapOf("a" to 1))
        assertEquals("{\n  \"a\": 1\n}", json)
    }
}

data class TestPoint(var x: Int, var y: Int)
