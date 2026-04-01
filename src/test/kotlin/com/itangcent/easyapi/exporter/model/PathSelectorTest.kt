package com.itangcent.easyapi.exporter.model

import junit.framework.TestCase

class PathSelectorTest : TestCase() {

    private data class Item(val path: String)

    private val items = listOf(
        Item("/a"),
        Item("/bb"),
        Item("/ccc"),
        Item("/dd")
    )

    fun testAll() {
        val result = PathSelector.ALL.select(items) { it.path }
        assertEquals(4, result.size)
    }

    fun testFirst() {
        val result = PathSelector.FIRST.select(items) { it.path }
        assertEquals(1, result.size)
        assertEquals("/a", result[0].path)
    }

    fun testLast() {
        val result = PathSelector.LAST.select(items) { it.path }
        assertEquals(1, result.size)
        assertEquals("/dd", result[0].path)
    }

    fun testShortest() {
        val result = PathSelector.SHORTEST.select(items) { it.path }
        assertEquals(1, result.size)
        assertEquals("/a", result[0].path)
    }

    fun testLongest() {
        val result = PathSelector.LONGEST.select(items) { it.path }
        assertEquals(1, result.size)
        assertEquals("/ccc", result[0].path)
    }

    fun testSelectEmptyList() {
        val empty = emptyList<Item>()
        assertEquals(0, PathSelector.FIRST.select(empty) { it.path }.size)
        assertEquals(0, PathSelector.LAST.select(empty) { it.path }.size)
        assertEquals(0, PathSelector.SHORTEST.select(empty) { it.path }.size)
        assertEquals(0, PathSelector.LONGEST.select(empty) { it.path }.size)
        assertEquals(0, PathSelector.ALL.select(empty) { it.path }.size)
    }

    fun testFromRuleValid() {
        assertEquals(PathSelector.FIRST, PathSelector.fromRule("first"))
        assertEquals(PathSelector.FIRST, PathSelector.fromRule("FIRST"))
        assertEquals(PathSelector.FIRST, PathSelector.fromRule("  First  "))
        assertEquals(PathSelector.LAST, PathSelector.fromRule("last"))
        assertEquals(PathSelector.SHORTEST, PathSelector.fromRule("shortest"))
        assertEquals(PathSelector.LONGEST, PathSelector.fromRule("longest"))
        assertEquals(PathSelector.ALL, PathSelector.fromRule("all"))
    }

    fun testFromRuleDefaultsToAll() {
        assertEquals(PathSelector.ALL, PathSelector.fromRule(null))
        assertEquals(PathSelector.ALL, PathSelector.fromRule(""))
        assertEquals(PathSelector.ALL, PathSelector.fromRule("  "))
        assertEquals(PathSelector.ALL, PathSelector.fromRule("unknown"))
    }
}
