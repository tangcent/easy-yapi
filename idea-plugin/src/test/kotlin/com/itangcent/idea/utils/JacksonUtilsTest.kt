package com.itangcent.idea.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

/**
 * Test case of [JacksonUtils]
 */
internal class JacksonUtilsTest {

    @Test
    fun testFromJson() {
        //null
        assertEquals(null as String?, JacksonUtils.fromJson(JacksonUtils.toJson(null)))

        //int
        assertEquals(1 as Int?, JacksonUtils.fromJson(JacksonUtils.toJson(1))!!)

        //long
        assertEquals(9999L, JacksonUtils.fromJson(JacksonUtils.toJson(9999L))!!)

        //float
        assertEquals(1.1f as Float?, JacksonUtils.fromJson(JacksonUtils.toJson(1.1f))!!)

        //double
        assertEquals(1.1 as Double?, JacksonUtils.fromJson(JacksonUtils.toJson(1.1))!!)

        //string
        assertEquals("hello world", JacksonUtils.fromJson(JacksonUtils.toJson("hello world")))

        //custom data
        val point = JacksonUtilsTestPoint(1, 2)
        assertEquals(point, JacksonUtils.fromJson(JacksonUtils.toJson(point)))

        //with generic list
        point.list = listOf(1, "2", mapOf<String, Any?>("x" to 1))
        assertEquals(point, JacksonUtils.fromJson(JacksonUtils.toJson(point)))

        assertNull(JacksonUtils.fromJson<Any>("{\"c\":\"java.lang.String\""))

        assertEquals("java.lang.Object,[\"java.lang.Object\",{}]", JacksonUtils.toJson(Any()))

        assertDoesNotThrow {
            JacksonUtils.fromJson<Any>("java.lang.Object,[\"java.lang.Object\",{}]").let {
                assertNotNull(it)
                //todo: make it true
                //assertEquals("java.lang.Object", it!!::class.java)
            }
        }
    }
}


class JacksonUtilsTestPoint {
    var x: Int? = null
    var y: Int? = null

    var list: List<Any?>? = null

    constructor(x: Int?, y: Int?) {
        this.x = x
        this.y = y
    }

    constructor()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JacksonUtilsTestPoint

        if (x != other.x) return false
        if (y != other.y) return false
        if (list != other.list) return false

        return true
    }

    override fun hashCode(): Int {
        var result = x ?: 0
        result = 31 * result + (y ?: 0)
        result = 31 * result + (list?.hashCode() ?: 0)
        return result
    }


}