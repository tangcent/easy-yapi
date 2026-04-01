package com.itangcent.easyapi.cache

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class CacheServiceTest {

    private lateinit var cacheService: CacheService

    @Before
    fun setUp() {
        cacheService = CacheService(mock())
    }

    @Test
    fun testPutAndGetString() {
        cacheService.putString("test.key", "test.value")
        assertEquals("test.value", cacheService.getString("test.key"))
    }

    @Test
    fun testGetNonExistentKey() {
        assertNull(cacheService.getString("nonexistent"))
    }

    @Test
    fun testOverwriteValue() {
        cacheService.putString("key", "value1")
        cacheService.putString("key", "value2")
        assertEquals("value2", cacheService.getString("key"))
    }

    @Test
    fun testMultipleKeys() {
        cacheService.putString("key1", "value1")
        cacheService.putString("key2", "value2")
        cacheService.putString("key3", "value3")

        assertEquals("value1", cacheService.getString("key1"))
        assertEquals("value2", cacheService.getString("key2"))
        assertEquals("value3", cacheService.getString("key3"))
    }

    @Test
    fun testEmptyValue() {
        cacheService.putString("empty.key", "")
        assertEquals("", cacheService.getString("empty.key"))
    }

    @Test
    fun testSpecialCharactersInKey() {
        cacheService.putString("key.with.special:chars", "value")
        assertEquals("value", cacheService.getString("key.with.special:chars"))
    }

    @Test
    fun testLongValue() {
        val longValue = "a".repeat(10000)
        cacheService.putString("long.key", longValue)
        assertEquals(longValue, cacheService.getString("long.key"))
    }
}
