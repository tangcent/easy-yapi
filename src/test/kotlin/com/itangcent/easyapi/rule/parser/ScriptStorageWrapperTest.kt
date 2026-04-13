package com.itangcent.easyapi.rule.parser

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.util.storage.SessionStorage
import org.junit.Assert.*

class ScriptStorageWrapperTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var storage: SessionStorage
    private lateinit var wrapper: ScriptStorageWrapper

    override fun setUp() {
        super.setUp()
        storage = SessionStorage.getInstance(project)
        wrapper = ScriptStorageWrapper(storage)
    }

    fun testSetAndGet() {
        wrapper.set("key", "value")
        assertEquals("value", wrapper.get("key"))
    }

    fun testSetAndGet_grouped() {
        wrapper.set("group1", "key", "value1")
        wrapper.set("group2", "key", "value2")
        assertEquals("value1", wrapper.get("group1", "key"))
        assertEquals("value2", wrapper.get("group2", "key"))
    }

    fun testPushAndPop() {
        wrapper.push("queue", "item1")
        wrapper.push("queue", "item2")
        assertEquals("item2", wrapper.pop("queue"))
        assertEquals("item1", wrapper.pop("queue"))
    }

    fun testPushAndPop_grouped() {
        wrapper.push("g", "queue", "item1")
        wrapper.push("g", "queue", "item2")
        assertEquals("item2", wrapper.pop("g", "queue"))
        assertEquals("item1", wrapper.pop("g", "queue"))
    }

    fun testPeek() {
        wrapper.push("queue", "item1")
        assertEquals("item1", wrapper.peek("queue"))
        assertEquals("item1", wrapper.peek("queue"))
    }

    fun testPeek_grouped() {
        wrapper.push("g", "queue", "item1")
        assertEquals("item1", wrapper.peek("g", "queue"))
    }

    fun testRemove() {
        wrapper.set("key", "value")
        wrapper.remove("key")
        assertNull(wrapper.get("key"))
    }

    fun testRemove_grouped() {
        wrapper.set("g", "key", "value")
        wrapper.remove("g", "key")
        assertNull(wrapper.get("g", "key"))
    }

    fun testKeys() {
        wrapper.set("k1", "v1")
        wrapper.set("k2", "v2")
        val keys = wrapper.keys()
        assertEquals(2, keys.size)
    }

    fun testKeys_grouped() {
        wrapper.set("g", "k1", "v1")
        val keys = wrapper.keys("g")
        assertEquals(1, keys.size)
    }

    fun testClear() {
        wrapper.set("key", "value")
        wrapper.clear()
        assertNull(wrapper.get("key"))
    }

    fun testClear_grouped() {
        wrapper.set("g", "key", "value")
        wrapper.clear("g")
        assertNull(wrapper.get("g", "key"))
    }
}
