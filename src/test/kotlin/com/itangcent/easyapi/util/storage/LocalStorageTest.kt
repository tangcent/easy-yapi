package com.itangcent.easyapi.util.storage

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.*

class LocalStorageTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testGetInstance() {
        val storage = LocalStorage.getInstance(project)
        assertNotNull("LocalStorage instance should not be null", storage)
    }

    fun testSetAndGet() {
        val storage = LocalStorage.getInstance(project)

        storage.set("test-group", "key1", "value1")

        val result = storage.get("test-group", "key1")
        assertEquals("Should retrieve the set value", "value1", result)
    }

    fun testGetNonExistentKey() {
        val storage = LocalStorage.getInstance(project)

        val result = storage.get("nonexistent-group", "nonexistent-key")

        assertNull("Should return null for non-existent key", result)
    }

    fun testRemove() {
        val storage = LocalStorage.getInstance(project)

        storage.set("test-group", "key1", "value1")
        storage.remove("test-group", "key1")

        val result = storage.get("test-group", "key1")
        assertNull("Should return null after remove", result)
    }

    fun testClear() {
        val storage = LocalStorage.getInstance(project)

        storage.set("test-group", "key1", "value1")
        storage.set("test-group", "key2", "value2")
        storage.clear("test-group")

        assertNull("key1 should be cleared", storage.get("test-group", "key1"))
        assertNull("key2 should be cleared", storage.get("test-group", "key2"))
    }

    fun testMultipleGroups() {
        val storage = LocalStorage.getInstance(project)

        storage.set("group1", "key1", "value1")
        storage.set("group2", "key1", "value2")

        assertEquals("group1 key1 should be value1", "value1", storage.get("group1", "key1"))
        assertEquals("group2 key1 should be value2", "value2", storage.get("group2", "key1"))
    }

    fun testComplexValue() {
        val storage = LocalStorage.getInstance(project)

        val complexValue = mapOf("name" to "test", "count" to 42)
        storage.set("test-group", "complex", complexValue)

        val result = storage.get("test-group", "complex") as? Map<*, *>
        assertNotNull("Should retrieve complex value", result)
        assertEquals("name should match", "test", result?.get("name"))
        assertNotNull("count should exist", result?.get("count"))
    }
}
