package com.itangcent.test

import com.itangcent.common.kit.KVUtils
import com.itangcent.common.kit.KitUtils
import com.itangcent.common.utils.asArrayList
import com.itangcent.common.utils.asHashMap
import com.itangcent.http.RequestUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.converter.ConvertWith
import org.junit.jupiter.params.provider.CsvSource
import java.util.*

/**
 * Test case for [KitUtils]
 */
class RequestUtilsTest {

    private fun options(): List<Map<String, Any?>> = listOf(
            mapOf("value" to 1, "desc" to "ONE"),
            mapOf("value" to 2, "desc" to "TWO"),
            mapOf("value" to 3, "desc" to "THREE")
    )

    @Test
    fun testToRawBody() {
        val info: HashMap<Any, Any?> = hashMapOf("x" to 1, "y" to 2)
        KVUtils.addComment(info, "x", "The value of the x axis")
        KVUtils.addKeyOptions(info, "x", options().map { it.asHashMap() }.asArrayList())
        assertEquals(hashMapOf("x" to 1, "y" to 2), RequestUtils.toRawBody(info, false))
    }

    @Test
    fun testParseRawBody() {
        val info: HashMap<Any, Any?> = hashMapOf("x" to 1, "y" to 2)
        KVUtils.addComment(info, "x", "The value of the x axis")
        KVUtils.addKeyOptions(info, "x", options().map { it.asHashMap() }.asArrayList())
        assertEquals("{\n" +
                "  \"x\": 1,\n" +
                "  \"y\": 2\n" +
                "}", RequestUtils.parseRawBody(info, false))
    }

    @ParameterizedTest
    @CsvSource("a,b", "a/,b", "a,/b", "a/,/b")
    fun testConcatPathToAB(pre: String, after: String) {
        assertEquals("a/b", RequestUtils.concatPath(pre, after))
    }

    @ParameterizedTest
    @CsvSource(
            value = ["a,null", "null,a"],
            nullValues = ["null"]
    )
    fun testConcatPathToA( pre: String?,
                         after: String?) {
        assertEquals("a", RequestUtils.concatPath(pre, after))
    }
}