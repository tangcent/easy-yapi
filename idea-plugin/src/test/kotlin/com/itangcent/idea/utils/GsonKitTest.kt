package com.itangcent.idea.utils

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.itangcent.intellij.extend.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow


/**
 * Test case for [GsonKit]
 */
class GsonKitTest {

    @Test
    fun testAsJsonElement() {
        assertTrue("".asJsonElement()!!.isJsonNull)
        assertTrue("1".asJsonElement()!!.isJsonPrimitive)
        assertTrue("{}".asJsonElement()!!.isJsonObject)
        assertTrue("[1]".asJsonElement()!!.isJsonArray)
    }

    @Test
    fun testAsMap() {

        //dumb by default
        assertDoesNotThrow { "".asJsonElement()!!.asMap() }
        assertDoesNotThrow { "1".asJsonElement()!!.asMap() }
        assertDoesNotThrow { "{}".asJsonElement()!!.asMap() }
        assertDoesNotThrow { "[1]".asJsonElement()!!.asMap() }

        //test result
        assertTrue("".asJsonElement()!!.asMap().isEmpty())
        assertTrue("1".asJsonElement()!!.asMap().isEmpty())
        assertTrue("[1]".asJsonElement()!!.asMap().isEmpty())
        "{x:\"1\"}".asJsonElement()!!.asJsonObject.getAsJsonPrimitive("x").apply {
            assertTrue(isString)
            assertEquals("1", asString)
        }

        //not dumb
        assertThrows(IllegalStateException::class.java) { "".asJsonElement()!!.asMap(false) }
        assertThrows(IllegalStateException::class.java) { "1".asJsonElement()!!.asMap(false) }
        assertDoesNotThrow { "{}".asJsonElement()!!.asMap(false) }
        assertThrows(IllegalStateException::class.java) { "[1]".asJsonElement()!!.asMap(false) }
    }

    @Test
    fun testAsList() {

        //dumb by default
        assertDoesNotThrow { "".asJsonElement()!!.asList() }
        assertDoesNotThrow { "1".asJsonElement()!!.asList() }
        assertDoesNotThrow { "{}".asJsonElement()!!.asList() }
        assertDoesNotThrow { "[1]".asJsonElement()!!.asList() }

        //test result
        assertTrue("".asJsonElement()!!.asList().isEmpty())
        assertTrue("1".asJsonElement()!!.asList().isEmpty())
        assertTrue("{x:1}".asJsonElement()!!.asList().isEmpty())
        assertEquals("1", "[\"1\"]".asJsonElement()!!.asJsonArray.first().asString)


        //not dumb
        assertThrows(IllegalStateException::class.java) { "".asJsonElement()!!.asList(false) }
        assertThrows(IllegalStateException::class.java) { "1".asJsonElement()!!.asList(false) }
        assertThrows(IllegalStateException::class.java) { "{}".asJsonElement()!!.asList(false) }
        assertDoesNotThrow { "[1]".asJsonElement()!!.asList(false) }
    }

    @Test
    fun testUnbox() {
        assertNull("null".asJsonElement()!!.unbox())
        assertEquals(1, ("1".asJsonElement()!!.unbox() as Number).toInt())
        assertEquals(true, "true".asJsonElement()!!.unbox())
        assertEquals("hello world", "\"hello world\"".asJsonElement()!!.unbox())

        assertEquals(hashMapOf("x" to "1"), "{x:\"1\"}".asJsonElement()!!.unbox())
        assertEquals(arrayListOf("1"), "[\"1\"]".asJsonElement()!!.unbox())

        assertEquals(1, (("1".asJsonElement() as JsonPrimitive).unbox() as Number).toInt())
        assertEquals(true, ("true".asJsonElement() as JsonPrimitive).unbox())
        assertEquals("hello world", ("\"hello world\"".asJsonElement() as JsonPrimitive).unbox())
    }

    @Test
    fun testSub() {
        assertNull((null as JsonElement?).sub("x"))
        assertNull("1".asJsonElement()!!.sub("x"))
        assertNull("true".asJsonElement()!!.sub("x"))
        assertNull("\"hello world\"".asJsonElement()!!.sub("x"))
        assertEquals("1", "{x:\"1\"}".asJsonElement()!!.sub("x")!!.unbox())
    }
}