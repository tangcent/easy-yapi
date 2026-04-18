package com.itangcent.easyapi.util.storage

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SimpleInMemoryStorage : AbstractStorage() {
    private val data: MutableMap<String, MutableMap<String, Any?>> = linkedMapOf()

    override fun getCache(group: String): MutableMap<String, Any?> {
        return data.getOrPut(group) { linkedMapOf() }
    }

    override fun onUpdate(group: String?, cache: MutableMap<String, Any?>) {
        val g = group ?: Storage.DEFAULT_GROUP
        if (cache.isEmpty()) {
            data.remove(g)
        } else {
            data[g] = cache
        }
    }
}

class AbstractStorageTest {

    private lateinit var storage: SimpleInMemoryStorage

    @Before
    fun setUp() {
        storage = SimpleInMemoryStorage()
    }

    @Test
    fun testSetAndGet() {
        storage.set("key1", "value1")
        assertEquals("value1", storage.get("key1"))
    }

    @Test
    fun testGetNonExistentReturnsNull() {
        assertNull(storage.get("nonexistent"))
    }

    @Test
    fun testSetAndGetWithGroup() {
        storage.set("group1", "key1", "value1")
        assertEquals("value1", storage.get("group1", "key1"))
    }

    @Test
    fun testRemove() {
        storage.set("key1", "value1")
        storage.remove("key1")
        assertNull(storage.get("key1"))
    }

    @Test
    fun testRemoveWithGroup() {
        storage.set("group1", "key1", "value1")
        storage.remove("group1", "key1")
        assertNull(storage.get("group1", "key1"))
    }

    @Test
    fun testKeys() {
        storage.set("key1", "value1")
        storage.set("key2", "value2")
        val keys = storage.keys()
        assertTrue("Keys should contain key1", keys.contains("key1"))
        assertTrue("Keys should contain key2", keys.contains("key2"))
    }

    @Test
    fun testClear() {
        storage.set("key1", "value1")
        storage.clear()
        assertNull(storage.get("key1"))
        assertTrue("Keys should be empty after clear", storage.keys().isEmpty())
    }

    @Test
    fun testClearGroup() {
        storage.set("key1", "value1")
        storage.set("group1", "key2", "value2")
        storage.clear("group1")
        assertNull(storage.get("group1", "key2"))
        assertEquals("value1", storage.get("key1"))
    }

    @Test
    fun testPushAndPop() {
        storage.push("queue", "item1")
        storage.push("queue", "item2")
        assertEquals("item2", storage.pop("queue"))
        assertEquals("item1", storage.pop("queue"))
    }

    @Test
    fun testPeek() {
        storage.push("queue", "item1")
        storage.push("queue", "item2")
        assertEquals("item2", storage.peek("queue"))
        assertEquals("item2", storage.peek("queue"))
    }

    @Test
    fun testPopEmptyReturnsNull() {
        assertNull(storage.pop("empty_queue"))
    }

    @Test
    fun testSetNullValue() {
        storage.set("key", null)
        assertNull(storage.get("key"))
    }

    @Test
    fun testSetDifferentTypes() {
        storage.set("string", "hello")
        storage.set("int", 42)
        storage.set("bool", true)
        assertEquals("hello", storage.get("string"))
        assertEquals(42, storage.get("int"))
        assertEquals(true, storage.get("bool"))
    }
}
