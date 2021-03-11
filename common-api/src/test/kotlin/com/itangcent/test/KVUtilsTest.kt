package com.itangcent.test

import com.itangcent.common.kit.KVUtils
import com.itangcent.common.kit.KitUtils
import com.itangcent.common.utils.asArrayList
import com.itangcent.common.utils.asHashMap
import com.itangcent.common.utils.getAs
import org.junit.jupiter.api.Assertions.assertEquals
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
            mapOf("value" to 3, "desc" to "THREE")
    )

    private fun constants(): List<Map<String, String>> = listOf(
            mapOf("name" to "ONE", "desc" to "first"),
            mapOf("name" to "TWO", "desc" to "second"),
            mapOf("name" to "THREE", "desc" to "third")
    )

    @Test
    fun testGetOptionDesc() {
        val options: List<Map<String, Any?>> = options()
        assertEquals("1 :ONE\n" +
                "2 :TWO\n" +
                "3 :THREE", KVUtils.getOptionDesc(options))
    }

    @Test
    fun testGetConstantDesc() {
        val constants: List<Map<String, Any?>> = constants()
        assertEquals("ONE :first\n" +
                "TWO :second\n" +
                "THREE :third", KVUtils.getConstantDesc(constants))
    }

    @Test
    fun testAddComment() {
        val info: HashMap<Any, Any?> = hashMapOf("x" to 1, "y" to 2)
        KVUtils.addComment(info, "x", "The value of the x axis")
        assertEquals(hashMapOf("x" to 1, "y" to 2, "@comment" to hashMapOf("x" to "The value of the x axis")), info)
    }

    @Test
    fun testAddKeyCommentsSimple() {
        val info: HashMap<Any, Any?> = hashMapOf("x" to 1, "y" to 2)
        KVUtils.addKeyComment(info, "x", "The value of the x axis")
        assertEquals(hashMapOf("x" to 1, "y" to 2, "@comment" to hashMapOf("x" to "The value of the x axis")), info)
    }

    @Test
    fun testAddKeyConstantsMulit() {
        val info: HashMap<Any, Any?> = hashMapOf("x" to 1, "y" to 2, "next" to hashMapOf("x" to 2, "y" to 3))
        KVUtils.addKeyComment(info, "next.x", "The value of the x axis")
        assertEquals(hashMapOf("x" to 1, "y" to 2, "next" to hashMapOf("x" to 2, "y" to 3,
                "@comment" to hashMapOf("x" to "The value of the x axis"))), info)
    }

    @Test
    fun testAddOptions() {
        val info: HashMap<Any, Any?> = hashMapOf("x" to 1, "y" to 2)
        KVUtils.addOptions(info, "x", options().map { it.asHashMap() }.asArrayList())
        assertEquals(hashMapOf("x" to 1, "y" to 2, "@comment" to hashMapOf("x@options" to
                listOf(mapOf("value" to 1, "desc" to "ONE"),
                        mapOf("value" to 2, "desc" to "TWO"),
                        mapOf("value" to 3, "desc" to "THREE")))), info)
    }

    @Test
    fun testAddKeyOptionsSimple() {
        val info: HashMap<Any, Any?> = hashMapOf("x" to 1, "y" to 2)
        KVUtils.addKeyOptions(info, "x", options().map { it.asHashMap() }.asArrayList())
        assertEquals(hashMapOf("x" to 1, "y" to 2, "@comment" to hashMapOf("x@options" to
                listOf(mapOf("value" to 1, "desc" to "ONE"),
                        mapOf("value" to 2, "desc" to "TWO"),
                        mapOf("value" to 3, "desc" to "THREE")))), info)
    }

    @Test
    fun testAddKeyOptionsMulit() {
        val info: HashMap<Any, Any?> = hashMapOf("x" to 1, "y" to 2, "next" to hashMapOf("x" to 2, "y" to 3))
        KVUtils.addKeyOptions(info, "next.x", options().map { it.asHashMap() }.asArrayList())
        assertEquals(hashMapOf("x" to 1, "y" to 2, "next" to hashMapOf("x" to 2, "y" to 3, "@comment" to hashMapOf("x@options" to
                listOf(mapOf("value" to 1, "desc" to "ONE"),
                        mapOf("value" to 2, "desc" to "TWO"),
                        mapOf("value" to 3, "desc" to "THREE"))))), info)
    }

    @Test
    fun testGetUltimateComment() {
        val info: HashMap<Any, Any?> = hashMapOf("x" to 1, "y" to 2)
        KVUtils.addComment(info, "x", "The value of the x axis")
        KVUtils.addKeyOptions(info, "x", options().map { it.asHashMap() }.asArrayList())
        assertEquals("The value of the x axis\n" +
                "1 :ONE\n" +
                "2 :TWO\n" +
                "3 :THREE", KVUtils.getUltimateComment(info.getAs("@comment"), "x"))
    }
}