package com.itangcent.easyapi.config.source

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RuntimeConfigSourceTest {

    private lateinit var source: RuntimeConfigSource

    @Before
    fun setUp() {
        source = RuntimeConfigSource("/test/module")
    }

    @Test
    fun testPriority() {
        assertEquals(0, source.priority)
    }

    @Test
    fun testSourceId() {
        assertEquals("runtime", source.sourceId)
    }

    @Test
    fun testCollect() = runBlocking {
        val entries = source.collect().toList()
        assertEquals(1, entries.size)
        assertEquals("module_path", entries[0].key)
        assertEquals("/test/module", entries[0].value)
        assertEquals("runtime", entries[0].sourceId)
    }

    @Test
    fun testSetModulePath() = runBlocking {
        source.setModulePath("/new/module")
        val entries = source.collect().toList()
        assertEquals(1, entries.size)
        assertEquals("/new/module", entries[0].value)
    }

    @Test
    fun testEmptyModulePath() = runBlocking {
        source.setModulePath("")
        val entries = source.collect().toList()
        assertEquals(1, entries.size)
        assertEquals("", entries[0].value)
    }

    @Test
    fun testMultipleCollects() = runBlocking {
        val entries1 = source.collect().toList()
        assertEquals("/test/module", entries1[0].value)

        source.setModulePath("/another/module")
        val entries2 = source.collect().toList()
        assertEquals("/another/module", entries2[0].value)
    }
}
