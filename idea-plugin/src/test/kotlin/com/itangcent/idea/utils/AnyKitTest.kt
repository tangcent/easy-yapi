package com.itangcent.idea.utils

import com.itangcent.intellij.extend.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test


/**
 * Test case for [AnyKit]
 */
class AnyKitTest {

    @Test
    fun testBooleanToInt() {
        assertEquals(1, true.toInt())
        assertEquals(0, false.toInt())
    }

    @Test
    fun testToPrettyString() {
        val nullObj: Any? = null
        assertNull(nullObj.toPrettyString())

        assertEquals("abc", "abc".toPrettyString())

        val array = arrayOf(1, 2, 3)
        assertEquals("[1, 2, 3]", array.toPrettyString())

        val list = listOf(1, 2, 3)
        assertEquals("[1, 2, 3]", list.toPrettyString())

        val map = mapOf("a" to 1, "b" to 2)
        assertEquals("{a: 1, b: 2}", map.toPrettyString())

        val obj = Any()
        assertEquals(obj.toString(), obj.toPrettyString())
    }

    @Test
    fun testAsHashMap() {
        val map = mapOf("a" to 1, "b" to 2)
        val hashMap = map.asHashMap<String, Any?>()
        assertInstanceOf(HashMap::class.java, hashMap)
        assertEquals(mapOf("a" to 1, "b" to 2), hashMap)

        val singleMap = mapOf("a" to 1)
        val singleHashMap = singleMap.asHashMap<String, Any?>()
        assertInstanceOf(HashMap::class.java, singleHashMap)
        assertEquals(mapOf("a" to 1), singleHashMap)

        val emptyMap = emptyMap<String, Any?>()
        val emptyHashMap = emptyMap.asHashMap<String, Any?>()
        assertInstanceOf(HashMap::class.java, emptyHashMap)
        assertTrue(emptyHashMap.isEmpty())

        val obj = Any()
        val otherEmptyHashMap = obj.asHashMap<String, Any?>()
        assertInstanceOf(HashMap::class.java, otherEmptyHashMap)
        assertTrue(otherEmptyHashMap.isEmpty())
    }

    @Test
    fun testTakeIfNotOriginal() {
        val obj1 = Any()
        val obj2 = 0
        val obj3 = ""
        val obj4 = arrayOf(0, 1)
        assertEquals(obj1, obj1.takeIfNotOriginal())
        assertNull(obj2.takeIfNotOriginal())
        assertNull(obj3.takeIfNotOriginal())
        assertEquals(obj4, obj4.takeIfNotOriginal())
    }

    @Test
    fun testIsSpecial() {
        val obj1: String? = null
        val obj2 = ""
        val obj3 = "0"
        val obj4 = "abc"
        val obj5 = arrayOf(0, 1)
        assertFalse(obj1.isSpecial())
        assertFalse(obj2.isSpecial())
        assertFalse(obj3.isSpecial())
        assertTrue(obj4.isSpecial())
        assertTrue(obj5.isSpecial())
    }

    @Test
    fun testTakeIfSpecial() {
        val obj1: String? = null
        val obj2 = ""
        val obj3 = "0"
        val obj4 = "abc"
        assertNull(obj1.takeIfSpecial())
        assertNull(obj2.takeIfSpecial())
        assertNull(obj3.takeIfSpecial())
        assertEquals(obj4, obj4.takeIfSpecial())
    }

    @Test
    fun testUnbox() {
        val obj1 = arrayOf(1, 2, 3)
        val obj2 = listOf(4, 5, 6)
        val obj3 = "abc"
        val obj4 = Any()
        assertEquals(1, obj1.unbox())
        assertEquals(4, obj2.unbox())
        assertEquals(obj3, obj3.unbox())
        assertEquals(obj4, obj4.unbox())
    }
}