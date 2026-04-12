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
    fun testPutAndGet() {
        cache.put("com.example.User@15", "value1")
        assertEquals("value1", cache.get("com.example.User@15"))
    }

    @Test
    fun testGetNonExistentKey() {
        assertNull(cache.get("nonexistent"))
    }

    @Test
    fun testOverwriteValue() {
        cache.put("key1", "value1")
        cache.put("key1", "value2")
        assertEquals("value2", cache.get("key1"))
    }

    @Test
    fun testClear() {
        cache.put("key1", "value1")
        cache.put("key2", "value2")
        cache.clear()
        assertNull(cache.get("key1"))
        assertNull(cache.get("key2"))
    }

    @Test
    fun testEmptyValue() {
        cache.put("key1", "")
        assertEquals("", cache.get("key1"))
    }

    @Test
    fun testMultipleKeys() {
        cache.put("key1", "value1")
        cache.put("key2", "value2")
        cache.put("key3", "value3")

        assertEquals("value1", cache.get("key1"))
        assertEquals("value2", cache.get("key2"))
        assertEquals("value3", cache.get("key3"))
    }

    @Test
    fun testComplexValue() {
        val complexValue = mapOf("name" to "test", "value" to 123)
        cache.put("key1", complexValue)
        assertEquals(complexValue, cache.get("key1"))
    }

    @Test
    fun testListValue() {
        val listValue = listOf("a", "b", "c")
        cache.put("key1", listValue)
        assertEquals(listValue, cache.get("key1"))
    }

    @Test
    fun testSpecialCharactersInKey() {
        cache.put("com.example.User\$Inner@15", "value1")
        assertEquals("value1", cache.get("com.example.User\$Inner@15"))
    }
}
