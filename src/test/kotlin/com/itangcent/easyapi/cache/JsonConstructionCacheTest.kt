package com.itangcent.easyapi.cache

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class JsonConstructionCacheTest {

    private lateinit var cache: JsonConstructionCache

    @Before
    fun setUp() {
        cache = JsonConstructionCache()
    }

    @Test
    fun testPutAndGetWithoutGroup() {
        cache.put("com.example.User@15", null, "value1")
        assertEquals("value1", cache.get("com.example.User@15", null))
    }

    @Test
    fun testPutAndGetWithGroup() {
        cache.put("com.example.User@15", "group1", "value1")
        assertEquals("value1", cache.get("com.example.User@15", "group1"))
    }

    @Test
    fun testGetNonExistentKey() {
        assertNull(cache.get("nonexistent", null))
    }

    @Test
    fun testGetNonExistentKeyWithGroup() {
        assertNull(cache.get("nonexistent", "group1"))
    }

    @Test
    fun testOverwriteValue() {
        cache.put("key1", null, "value1")
        cache.put("key1", null, "value2")
        assertEquals("value2", cache.get("key1", null))
    }

    @Test
    fun testDifferentGroupsIsolated() {
        cache.put("key1", "group1", "value1")
        cache.put("key1", "group2", "value2")
        assertEquals("value1", cache.get("key1", "group1"))
        assertEquals("value2", cache.get("key1", "group2"))
    }

    @Test
    fun testGroupAndNoGroupIsolated() {
        cache.put("key1", null, "valueNoGroup")
        cache.put("key1", "group1", "valueWithGroup")
        assertEquals("valueNoGroup", cache.get("key1", null))
        assertEquals("valueWithGroup", cache.get("key1", "group1"))
    }

    @Test
    fun testClear() {
        cache.put("key1", null, "value1")
        cache.put("key2", "group1", "value2")
        cache.clear()
        assertNull(cache.get("key1", null))
        assertNull(cache.get("key2", "group1"))
    }

    @Test
    fun testEmptyValue() {
        cache.put("key1", null, "")
        assertEquals("", cache.get("key1", null))
    }

    @Test
    fun testEmptyGroup() {
        cache.put("key1", "", "value1")
        assertEquals("value1", cache.get("key1", ""))
    }

    @Test
    fun testBlankGroup() {
        cache.put("key1", "   ", "value1")
        assertEquals("value1", cache.get("key1", "   "))
    }

    @Test
    fun testMultipleKeys() {
        cache.put("key1", null, "value1")
        cache.put("key2", null, "value2")
        cache.put("key3", null, "value3")

        assertEquals("value1", cache.get("key1", null))
        assertEquals("value2", cache.get("key2", null))
        assertEquals("value3", cache.get("key3", null))
    }

    @Test
    fun testComplexValue() {
        val complexValue = mapOf("name" to "test", "value" to 123)
        cache.put("key1", null, complexValue)
        assertEquals(complexValue, cache.get("key1", null))
    }

    @Test
    fun testListValue() {
        val listValue = listOf("a", "b", "c")
        cache.put("key1", null, listValue)
        assertEquals(listValue, cache.get("key1", null))
    }

    @Test
    fun testSpecialCharactersInKey() {
        cache.put("com.example.User\$Inner@15", null, "value1")
        assertEquals("value1", cache.get("com.example.User\$Inner@15", null))
    }

    @Test
    fun testSpecialCharactersInGroup() {
        cache.put("key1", "group:with:special:chars", "value1")
        assertEquals("value1", cache.get("key1", "group:with:special:chars"))
    }
}
