package com.itangcent.common.kit

import com.itangcent.common.utils.asArrayList
import com.itangcent.common.utils.asHashMap
import com.itangcent.common.utils.getAs
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

/**
 * Test case for [KitUtils]
 */
@ExtendWith
class KVUtilsTest {

    private fun options(): List<Map<String, Any?>> = listOf(
            mapOf("value" to 1, "desc" to "ONE"),
            mapOf("value" to 2, "desc" to "TWO"),
            mapOf("value" to 3, "desc" to "THREE"),
            mapOf("value" to 4, "desc" to null),
            mapOf("value" to null, "desc" to "FIVE")
    )

    private fun constants(): List<Map<String, Any?>> = listOf(
            mapOf("name" to "ONE", "desc" to "first"),
            mapOf("name" to "TWO", "desc" to "second"),
            mapOf("name" to "THREE", "desc" to "third"),
            mapOf("name" to 4, "desc" to null),
            mapOf("name" to null, "desc" to "FIVE")
    )

    @Test
    fun testGetOptionDesc() {
        val options: List<Map<String, Any?>> = options()
        assertEquals("1 :ONE\n" +
                "2 :TWO\n" +
                "3 :THREE\n4", KVUtils.getOptionDesc(options))
    }

    @Test
    fun testGetConstantDesc() {
        val constants: List<Map<String, Any?>> = constants()
        assertEquals("ONE :first\n" +
                "TWO :second\n" +
                "THREE :third\n4", KVUtils.getConstantDesc(constants))
    }

    @Test
    fun testAddComment() {
        val info: HashMap<Any, Any?> = hashMapOf("x" to 1, "y" to 2)
        KVUtils.addComment(info, "x", "The value of the x axis")
        assertEquals(hashMapOf("x" to 1, "y" to 2, "@comment" to hashMapOf("x" to "The value of the x axis")), info)
    }

    @Test
    fun testAddKeyComments() {
        val info: HashMap<Any, Any?> = hashMapOf("x" to 1, "y" to 2, "next" to hashMapOf("x" to 2, "y" to 3))
        assertTrue(KVUtils.addKeyComment(info, "x", "The value of the x axis"))
        assertEquals(hashMapOf("x" to 1, "y" to 2, "next" to hashMapOf("x" to 2, "y" to 3),
                "@comment" to hashMapOf("x" to "The value of the x axis")), info)
        assertTrue(KVUtils.addKeyComment(info, "x", "The value of the x axis"))
        assertTrue(KVUtils.addKeyComment(info, "y", "The value of the y axis"))
        assertTrue(KVUtils.addKeyComment(info, "next.x", "The value of the next.x axis"))
        assertEquals(hashMapOf("x" to 1, "y" to 2, "next" to hashMapOf("x" to 2, "y" to 3,
                "@comment" to hashMapOf("x" to "The value of the next.x axis")),
                "@comment" to hashMapOf("x" to "The value of the x axis\nThe value of the x axis",
                        "y" to "The value of the y axis")), info)

        //list
        val list = listOf(hashMapOf("x" to 1, "y" to 2, "next" to hashMapOf("x" to 2, "y" to 3)))
        assertTrue(KVUtils.addKeyComment(list, "x", "The value of the x axis"))
        assertEquals(listOf(hashMapOf("x" to 1, "y" to 2, "next" to hashMapOf("x" to 2, "y" to 3),
                "@comment" to hashMapOf("x" to "The value of the x axis"))), list)
        assertFalse(KVUtils.addKeyComment(emptyList<Any?>(), "x", "The value of the x axis"))

        //array
        val array = arrayOf(hashMapOf("x" to 1, "y" to 2, "next" to hashMapOf("x" to 2, "y" to 3)))
        assertTrue(KVUtils.addKeyComment(array, "x", "The value of the x axis"))
        assertArrayEquals(arrayOf(hashMapOf("x" to 1, "y" to 2, "next" to hashMapOf("x" to 2, "y" to 3),
                "@comment" to hashMapOf("x" to "The value of the x axis"))), array)
        assertFalse(KVUtils.addKeyComment(emptyArray<Any?>(), "x", "The value of the x axis"))

        //others
        assertFalse(KVUtils.addKeyComment(1, "x", "The value of the x axis"))
        assertFalse(KVUtils.addKeyComment(null, "x", "The value of the x axis"))

    }

    @Test
    fun testAddOptions() {
        val info: HashMap<Any, Any?> = hashMapOf("x" to 1, "y" to 2)
        KVUtils.addOptions(info, "x", options().map { it.asHashMap() }.asArrayList())
        assertEquals(hashMapOf("x" to 1, "y" to 2, "@comment" to hashMapOf("x@options" to
                listOf(mapOf("value" to 1, "desc" to "ONE"),
                        mapOf("value" to 2, "desc" to "TWO"),
                        mapOf("value" to 3, "desc" to "THREE"),
                        mapOf("value" to 4, "desc" to null),
                        mapOf("value" to null, "desc" to "FIVE")))), info)
        KVUtils.addOptions(info, "x", arrayListOf(hashMapOf("value" to 6, "desc" to "SIX")))
        assertEquals(hashMapOf("x" to 1, "y" to 2, "@comment" to hashMapOf("x@options" to
                listOf(mapOf("value" to 1, "desc" to "ONE"),
                        mapOf("value" to 2, "desc" to "TWO"),
                        mapOf("value" to 3, "desc" to "THREE"),
                        mapOf("value" to 4, "desc" to null),
                        mapOf("value" to null, "desc" to "FIVE"),
                        mapOf("value" to 6, "desc" to "SIX")))), info)
    }

    @Test
    fun testAddKeyOptions() {
        val info: HashMap<Any, Any?> = hashMapOf("x" to 1, "y" to 2, "next" to hashMapOf("x" to 2, "y" to 3))
        KVUtils.addKeyOptions(info, "x", options().map { it.asHashMap() }.asArrayList())
        assertEquals(hashMapOf("x" to 1, "y" to 2, "next" to hashMapOf("x" to 2, "y" to 3),
                "@comment" to hashMapOf("x@options" to
                        listOf(mapOf("value" to 1, "desc" to "ONE"),
                                mapOf("value" to 2, "desc" to "TWO"),
                                mapOf("value" to 3, "desc" to "THREE"),
                                mapOf("value" to 4, "desc" to null),
                                mapOf("value" to null, "desc" to "FIVE")))), info)

        KVUtils.addKeyOptions(info, "next.x", options().map { it.asHashMap() }.asArrayList())
        assertEquals(hashMapOf("x" to 1, "y" to 2, "next" to hashMapOf("x" to 2, "y" to 3, "@comment" to hashMapOf("x@options" to
                listOf(mapOf("value" to 1, "desc" to "ONE"),
                        mapOf("value" to 2, "desc" to "TWO"),
                        mapOf("value" to 3, "desc" to "THREE"),
                        mapOf("value" to 4, "desc" to null),
                        mapOf("value" to null, "desc" to "FIVE")))),
                "@comment" to hashMapOf("x@options" to
                        listOf(mapOf("value" to 1, "desc" to "ONE"),
                                mapOf("value" to 2, "desc" to "TWO"),
                                mapOf("value" to 3, "desc" to "THREE"),
                                mapOf("value" to 4, "desc" to null),
                                mapOf("value" to null, "desc" to "FIVE")))), info)


        //list
        val list = listOf(hashMapOf("x" to 1, "y" to 2, "next" to hashMapOf("x" to 2, "y" to 3)))
        assertTrue(KVUtils.addKeyOptions(list, "x", options().map { it.asHashMap() }.asArrayList()))
        assertEquals(listOf(hashMapOf("x" to 1, "y" to 2, "next" to hashMapOf("x" to 2, "y" to 3),
                "@comment" to hashMapOf("x@options" to
                        listOf(mapOf("value" to 1, "desc" to "ONE"),
                                mapOf("value" to 2, "desc" to "TWO"),
                                mapOf("value" to 3, "desc" to "THREE"),
                                mapOf("value" to 4, "desc" to null),
                                mapOf("value" to null, "desc" to "FIVE"))))), list)
        assertFalse(KVUtils.addKeyOptions(emptyList<Any?>(), "x", options().map { it.asHashMap() }.asArrayList()))

        //array
        val array = arrayOf(hashMapOf("x" to 1, "y" to 2, "next" to hashMapOf("x" to 2, "y" to 3)))
        assertTrue(KVUtils.addKeyOptions(array, "x", options().map { it.asHashMap() }.asArrayList()))
        assertArrayEquals(arrayOf(hashMapOf("x" to 1, "y" to 2, "next" to hashMapOf("x" to 2, "y" to 3),
                "@comment" to hashMapOf("x@options" to
                        listOf(mapOf("value" to 1, "desc" to "ONE"),
                                mapOf("value" to 2, "desc" to "TWO"),
                                mapOf("value" to 3, "desc" to "THREE"),
                                mapOf("value" to 4, "desc" to null),
                                mapOf("value" to null, "desc" to "FIVE"))))), array)
        assertFalse(KVUtils.addKeyOptions(emptyArray<Any?>(), "x", options().map { it.asHashMap() }.asArrayList()))

        //others
        assertFalse(KVUtils.addKeyOptions(1, "x", options().map { it.asHashMap() }.asArrayList()))
        assertFalse(KVUtils.addKeyOptions(null, "x", options().map { it.asHashMap() }.asArrayList()))
    }

    @Test
    fun testGetUltimateComment() {
        val info: HashMap<Any, Any?> = hashMapOf("x" to 1, "y" to 2, "z" to 3)
        KVUtils.addComment(info, "x", "The value of the x axis")
        KVUtils.addComment(info, "y", "The value of the y axis")
        KVUtils.addKeyOptions(info, "x", options().map { it.asHashMap() }.asArrayList())
        KVUtils.addKeyOptions(info, "z", options().map { it.asHashMap() }.asArrayList())
        assertEquals("The value of the x axis\n" +
                "1 :ONE\n" +
                "2 :TWO\n" +
                "3 :THREE\n4", KVUtils.getUltimateComment(info.getAs("@comment"), "x"))
        assertEquals("The value of the y axis", KVUtils.getUltimateComment(info.getAs("@comment"), "y"))
        assertEquals("1 :ONE\n" +
                "2 :TWO\n" +
                "3 :THREE\n4", KVUtils.getUltimateComment(info.getAs("@comment"), "z"))
        assertEquals("", KVUtils.getUltimateComment(null, "x"))
        assertEquals("", KVUtils.getUltimateComment(info.getAs("@comment"), null))
    }
}