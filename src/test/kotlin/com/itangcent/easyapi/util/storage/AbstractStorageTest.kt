package com.itangcent.easyapi.util.storage

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AbstractStorageTest {

    private lateinit var storage: TestStorage

    @Before
    fun setUp() {
        storage = TestStorage()
    }

    @Test
    fun testSetAndGet_defaultGroup() {
        storage.set("key1", "value1")
        assertEquals("value1", storage.get("key1"))
    }

    @Test
    fun testSetAndGet_customGroup() {
        storage.set("group1", "key1", "value1")
        assertEquals("value1", storage.get("group1", "key1"))
    }

    @Test
    fun testGet_nonExistent() {
        assertNull(storage.get("missing"))
    }

    @Test
    fun testSet_null() {
        storage.set("key1", "value1")
        storage.set("key1", null)
        assertNull(storage.get("key1"))
    }

    @Test
    fun testRemove_defaultGroup() {
        storage.set("key1", "value1")
        storage.remove("key1")
        assertNull(storage.get("key1"))
    }

    @Test
    fun testRemove_customGroup() {
        storage.set("g", "key1", "value1")
        storage.remove("g", "key1")
        assertNull(storage.get("g", "key1"))
    }

    @Test
    fun testPushAndPop() {
        storage.push("queue", "a")
        storage.push("queue", "b")
        assertEquals("b", storage.pop("queue"))
        assertEquals("a", storage.pop("queue"))
        assertNull(storage.pop("queue"))
    }

    @Test
    fun testPushAndPeek() {
        storage.push("queue", "a")
        assertEquals("a", storage.peek("queue"))
        assertEquals("a", storage.peek("queue")) // peek doesn't remove
    }

    @Test
    fun testPeek_empty() {
        assertNull(storage.peek("empty"))
    }

    @Test
    fun testPop_empty() {
        assertNull(storage.pop("empty"))
    }

    @Test
    fun testPushAndPop_customGroup() {
        storage.push("g", "queue", "a")
        storage.push("g", "queue", "b")
        assertEquals("b", storage.pop("g", "queue"))
        assertEquals("a", storage.pop("g", "queue"))
    }

    @Test
    fun testPeek_customGroup() {
        storage.push("g", "queue", "a")
        assertEquals("a", storage.peek("g", "queue"))
    }

    @Test
    fun testKeys_defaultGroup() {
        storage.set("k1", "v1")
        storage.set("k2", "v2")
        val keys = storage.keys()
        assertTrue(keys.contains("k1"))
        assertTrue(keys.contains("k2"))
    }

    @Test
    fun testKeys_customGroup() {
        storage.set("g", "k1", "v1")
        val keys = storage.keys("g")
        assertTrue(keys.contains("k1"))
    }

    @Test
    fun testClear_defaultGroup() {
        storage.set("k1", "v1")
        storage.set("k2", "v2")
        storage.clear()
        assertNull(storage.get("k1"))
    }

    @Test
    fun testClear_customGroup() {
        storage.set("g", "k1", "v1")
        storage.set("g", "k2", "v2")
        storage.clear("g")
        assertNull(storage.get("g", "k1"))
    }

    @Test
    fun testClear_doesNotAffectOtherGroups() {
        storage.set("g1", "k1", "v1")
        storage.set("g2", "k2", "v2")
        storage.clear("g1")
        assertNull(storage.get("g1", "k1"))
        assertEquals("v2", storage.get("g2", "k2"))
    }

    @Test
    fun testGroupIsolation() {
        storage.set("g1", "key", "val1")
        storage.set("g2", "key", "val2")
        assertEquals("val1", storage.get("g1", "key"))
        assertEquals("val2", storage.get("g2", "key"))
    }

    @Test
    fun testConstants() {
        assertEquals("__default_local_group", Storage.DEFAULT_GROUP)
        assertEquals("__null", Storage.NULL)
    }

    /**
     * Concrete implementation of AbstractStorage for testing.
     */
    private class TestStorage : AbstractStorage() {
        private val data = mutableMapOf<String, MutableMap<String, Any?>>()

        override fun getCache(group: String): MutableMap<String, Any?> {
            return data.getOrPut(group) { mutableMapOf() }
        }

        override fun onUpdate(group: String?, cache: MutableMap<String, Any?>) {
            // no-op for tests
        }
    }
}
