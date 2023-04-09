package com.itangcent.utils

import com.itangcent.common.kit.toJson
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AnyKitKtTest {

    @Test
    fun isCollections() {
        assertFalse(1.isCollections())
        assertFalse(1L.isCollections())
        assertFalse("str".isCollections())
        assertFalse(true.isCollections())
        assertFalse(null.isCollections())
        assertTrue(emptyArray<Any>().isCollections())
        assertTrue(emptyList<Any>().isCollections())
        assertTrue(emptySet<Any>().isCollections())
        assertTrue(emptyMap<Any, Any>().isCollections())
        assertTrue(arrayOf(1).isCollections())
        assertTrue(listOf("str").isCollections())
        assertTrue(setOf(true).isCollections())
        assertTrue(mapOf(1 to "xx").isCollections())
    }

    @Test
    fun subMutable() {
        assertNull(
            mapOf("a" to "b").subMutable("a")
        )
        assertNull(
            mapOf("a" to "b").subMutable("x")
        )
        assertEquals(
            "{\"a\":{\"b\":\"d\"}}",
            hashMapOf("a" to mapOf("b" to "c")).also {
                it.subMutable("a")!!["b"] = "d"
            }.toJson()
        )
        assertEquals(
            "{\"a\":{\"b\":\"c\",\"x\":\"y\"}}",
            hashMapOf("a" to mapOf("b" to "c")).also {
                it.subMutable("a")!!["x"] = "y"
            }.toJson()
        )
        assertEquals(
            "{\"a\":{\"b\":\"d\"}}",
            hashMapOf("a" to hashMapOf("b" to "c")).also {
                it.subMutable("a")!!["b"] = "d"
            }.toJson()
        )
        assertEquals(
            "{\"a\":{\"b\":\"c\",\"x\":\"y\"}}",
            hashMapOf("a" to linkedMapOf("b" to "c")).also {
                it.subMutable("a")!!["x"] = "y"
            }.toJson()
        )
        assertEquals(
            "{\"x\":{\"b\":\"c\"},\"a\":\"b\"}",
            hashMapOf("a" to "b").also {
                it.subMutable("x")!!["b"] = "c"
            }.toJson()
        )
    }
}