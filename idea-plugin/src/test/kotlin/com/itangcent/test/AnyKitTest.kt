package com.itangcent.test

import com.itangcent.intellij.extend.asArrayList
import com.itangcent.intellij.extend.asHashMap
import com.itangcent.intellij.extend.toInt
import com.itangcent.intellij.extend.toPrettyString
import org.junit.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.runner.RunWith
import org.junit.runners.JUnit4


/**
 * Test case for [AnyKit]
 */
@RunWith(JUnit4::class)
class AnyKitTest {

    @Test
    fun testToInt() {
        assertEquals(1, true.toInt())
        assertEquals(0, false.toInt())
    }

    @Test
    fun testAsHashMap() {
        val map = mapOf("x" to 1)
        val hashMap = map.asHashMap()
        assertEquals(HashMap::class, hashMap::class)
        assertFalse(map === hashMap)
        assertEquals(map, hashMap)
        assertTrue(hashMap === hashMap.asHashMap())
    }

    @Test
    fun testAsArrayList() {
        val list = listOf("x", "y")
        val arrayList = list.asArrayList()
        assertEquals(ArrayList::class, arrayList::class)
        assertFalse(list === arrayList)
        assertEquals(list, arrayList)
        assertTrue(arrayList === arrayList.asArrayList())
    }

    @Test
    fun testToPrettyString() {
        assertEquals(null, null.toPrettyString())
        assertEquals("hello world", "hello world".toPrettyString())
        assertEquals("1", 1.toPrettyString())
        assertEquals("1.0", 1.0.toPrettyString())
        assertEquals("[x, 1]", listOf("x", 1).toPrettyString())
        assertEquals("[y, 2]", arrayOf("y", 2).toPrettyString())
        assertEquals("{x: 1, y: 2}", mapOf("x" to 1, "y" to 2).toPrettyString())
    }
}