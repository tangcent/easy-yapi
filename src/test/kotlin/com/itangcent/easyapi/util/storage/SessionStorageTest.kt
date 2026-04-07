package com.itangcent.easyapi.util.storage

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SessionStorageTest {

    private lateinit var storage: SessionStorage

    @Before
    fun setUp() {
        storage = SessionStorage()
    }

    @Test
    fun testSetAndGet() {
        storage.set("key", "value")
        assertEquals("value", storage.get("key"))
    }

    @Test
    fun testGet_nonExistent() {
        assertNull(storage.get("nonexistent"))
    }

    @Test
    fun testSet_overwrite() {
        storage.set("key", "value1")
        storage.set("key", "value2")
        assertEquals("value2", storage.get("key"))
    }

    @Test
    fun testSet_null() {
        storage.set("key", null)
        assertNull(storage.get("key"))
    }

    @Test
    fun testRemove() {
        storage.set("key", "value")
        storage.remove("key")
        assertNull(storage.get("key"))
    }

    @Test
    fun testKeys() {
        storage.set("key1", "value1")
        storage.set("key2", "value2")
        val keys = storage.keys()
        assertEquals(2, keys.size)
        assertTrue(keys.contains("key1"))
        assertTrue(keys.contains("key2"))
    }

    @Test
    fun testClear() {
        storage.set("key1", "value1")
        storage.set("key2", "value2")
        storage.clear()
        assertNull(storage.get("key1"))
        assertNull(storage.get("key2"))
        assertEquals(0, storage.keys().size)
    }

    @Test
    fun testGroupedSetAndGet() {
        storage.set("group1", "key", "value1")
        storage.set("group2", "key", "value2")
        assertEquals("value1", storage.get("group1", "key"))
        assertEquals("value2", storage.get("group2", "key"))
    }

    @Test
    fun testGroupedKeys() {
        storage.set("group1", "key1", "value1")
        storage.set("group1", "key2", "value2")
        val keys = storage.keys("group1")
        assertEquals(2, keys.size)
    }

    @Test
    fun testGroupedClear() {
        storage.set("group1", "key", "value")
        storage.set("group2", "key", "value")
        storage.clear("group1")
        assertNull(storage.get("group1", "key"))
        assertEquals("value", storage.get("group2", "key"))
    }

    @Test
    fun testPushAndPop() {
        storage.push("queue", "item1")
        storage.push("queue", "item2")
        storage.push("queue", "item3")
        assertEquals("item3", storage.pop("queue"))
        assertEquals("item2", storage.pop("queue"))
        assertEquals("item1", storage.pop("queue"))
        assertNull(storage.pop("queue"))
    }

    @Test
    fun testPeek_doesNotRemove() {
        storage.push("queue", "item1")
        storage.push("queue", "item2")
        assertEquals("item2", storage.peek("queue"))
        assertEquals("item2", storage.peek("queue"))
    }

    @Test
    fun testCleanup() = runBlocking {
        storage.set("key1", "value1")
        storage.set("key2", "value2")
        storage.cleanup()
        assertNull(storage.get("key1"))
        assertNull(storage.get("key2"))
    }

    @Test
    fun testSetAndGet_differentTypes() {
        storage.set("int", 42)
        storage.set("list", listOf(1, 2, 3))
        storage.set("map", mapOf("a" to 1))
        assertEquals(42, storage.get("int"))
        assertEquals(listOf(1, 2, 3), storage.get("list"))
        assertEquals(mapOf("a" to 1), storage.get("map"))
    }
}
