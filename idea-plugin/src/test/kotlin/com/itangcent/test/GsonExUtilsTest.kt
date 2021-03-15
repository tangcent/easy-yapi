package com.itangcent.test

import com.itangcent.idea.utils.GsonExUtils
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import org.junit.runners.JUnit4


/**
 * Test case for [GsonExUtils]
 */
@Suppress("CAST_NEVER_SUCCEEDS")
@RunWith(JUnit4::class)
class GsonExUtilsTest {

    @Test
    fun testFromJson() {
        //null
        assertEquals(null as String?, GsonExUtils.fromJson(GsonExUtils.toJson(null)))

        //int
        assertEquals(1 as Int?, GsonExUtils.fromJson(GsonExUtils.toJson(1))!!)

        //long
        assertEquals(9999L, GsonExUtils.fromJson(GsonExUtils.toJson(9999L))!!)

        //float
        assertEquals(1.1f as Float?, GsonExUtils.fromJson(GsonExUtils.toJson(1.1f))!!)

        //double
        assertEquals(1.1 as Double?, GsonExUtils.fromJson(GsonExUtils.toJson(1.1))!!)

        //string
        assertEquals("hello world", GsonExUtils.fromJson(GsonExUtils.toJson("hello world")))

        //custom data
        val point = Point(1, 2)
        assertEquals(point, GsonExUtils.fromJson(GsonExUtils.toJson(point)))
    }

    @Test
    fun prettyJson() {
        assertEquals("{\n  \"a\": 1\n}", GsonExUtils.prettyJson("{a:1}"))
        assertEquals("{\n" +
                "  \"a\": 1.0,\n" +
                "  \"b\": \"1.1f\"\n" +
                "}", GsonExUtils.prettyJson("{a:1.0,b:1.1f}"))
    }
}

data class Point(var x: Int, var y: Int)