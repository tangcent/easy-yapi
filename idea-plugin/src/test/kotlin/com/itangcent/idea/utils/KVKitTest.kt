package com.itangcent.idea.utils

import com.itangcent.common.utils.KV
import com.itangcent.intellij.util.*
import org.junit.Assert.*
import org.junit.jupiter.api.Test
import java.util.function.BiConsumer


/**
 * Test case for [KVKit]
 */
class KVKitTest {

    @Test
    fun testForEachValid() {
        KV.by("a", "A").forEachValid(BiConsumer { t, u ->
            assertEquals("a", t)
            assertEquals("A", u)
        })

        KV.by("", "A").forEachValid(BiConsumer { t, u ->
            assertEquals("key", t)
            assertEquals("A", u)
        })

        KV.by("a", "A").set("@a", "").forEachValid(BiConsumer { t, u ->
            assertEquals("a", t)
            assertEquals("A", u)
        })

        KV.by("a", "A").forEachValid { t, u ->
            assertEquals("a", t)
            assertEquals("A", u)
        }

        KV.by("", "A").forEachValid { t, u ->
            assertEquals("key", t)
            assertEquals("A", u)
        }

        KV.by("a", "A").set("@a", "").forEachValid { t, u ->
            assertEquals("a", t)
            assertEquals("A", u)
        }
        mapOf("a" to "A").forEachValid { t, u ->
            assertEquals("a", t)
            assertEquals("A", u)
        }

        mapOf("" to "A").forEachValid { t, u ->
            assertEquals("key", t)
            assertEquals("A", u)
        }

        mapOf("a" to "A", "@a" to "").forEachValid { t, u ->
            assertEquals("a", t)
            assertEquals("A", u)
        }
    }

    @Test
    fun testValidSize() {
        assertEquals(0, emptyMap<String, String>().validSize())
        assertEquals(1, mapOf("a" to "A").validSize())
        assertEquals(0, mapOf("@a" to "A").validSize())
        assertEquals(1, mapOf(1 to "a").validSize())
        assertEquals(1, mapOf("a" to "A", "@a" to "A").validSize())
        assertEquals(2, mapOf<Any, Any>("a" to "A", "@a" to "A", 1 to "a").validSize())
    }

    @Test
    fun testFlatValid() {

        val sb = StringBuilder()
        mapOf<Any?, Any?>("a" to "A",
                null to mapOf("x" to 1, "y" to null),
                "map" to mapOf("e" to 2, "l" to listOf("a"), "arr" to arrayOf("arr1")),
                "list" to listOf("e", mapOf("a" to "b")),
                listOf("a") to "y",
                mapOf("a" to "b") to "z"
        ).flatValid(object : FieldConsumer {
            override fun consume(parent: Map<*, *>?, path: String, key: String, value: Any?) {
                sb.append("$path $key $value\n")
            }
        })
        assertEquals("a a A\n" +
                "map.e e 2\n" +
                "map.l[0] l a\n" +
                "map.arr[0] arr arr1\n" +
                "list[0] list e\n" +
                "list[1].a a b\n" +
                "[\"a\"] [\"a\"] y\n" +
                "{\"a\":\"b\"} {\"a\":\"b\"} z\n", sb.toString())
    }


    @Test
    fun testIsComplex() {
        assertFalse(null.isComplex())
        assertFalse("".isComplex())
        assertFalse("str".isComplex())
        assertFalse(1.isComplex())
        assertFalse(1L.isComplex())
        assertFalse(1.0.isComplex())
        assertFalse(1.0f.isComplex())
        assertFalse(Magics.FILE_STR.isComplex())

        assertFalse(emptyArray<String>().isComplex())
        assertFalse(emptyList<String>().isComplex())
        assertFalse(emptyMap<String, String>().isComplex())
        assertFalse(arrayOf("a").isComplex())
        assertFalse(listOf(1).isComplex())
        assertFalse(mapOf("a" to 1).isComplex())
        assertFalse(arrayOf("a", emptyList<String>()).isComplex())
        assertFalse(listOf(1, emptyList<String>()).isComplex())
        assertFalse(mapOf("a" to 1, "b" to emptyArray<String>()).isComplex())
        assertTrue(arrayOf("a", emptyMap<String, String>()).isComplex())
        assertTrue(listOf(1, emptyMap<String, String>()).isComplex())
        assertTrue(mapOf("a" to 1, "b" to emptyMap<String, String>()).isComplex())
        assertTrue(mapOf("a" to 1, "@a" to 1, "b" to emptyMap<String, String>()).isComplex())

        assertFalse(null.isComplex(false))
        assertFalse("".isComplex(false))
        assertFalse("str".isComplex(false))
        assertFalse(1.isComplex(false))
        assertFalse(1L.isComplex(false))
        assertFalse(1.0.isComplex(false))
        assertFalse(1.0f.isComplex(false))
        assertFalse(Magics.FILE_STR.isComplex(false))

        assertFalse(emptyArray<String>().isComplex(false))
        assertFalse(emptyList<String>().isComplex(false))
        assertTrue(emptyMap<String, String>().isComplex(false))
        assertFalse(arrayOf("a").isComplex(false))
        assertFalse(listOf(1).isComplex(false))
        assertTrue(mapOf("a" to 1).isComplex(false))
        assertFalse(arrayOf("a", emptyList<String>()).isComplex(false))
        assertFalse(listOf(1, emptyList<String>()).isComplex(false))
        assertTrue(mapOf("a" to 1, "@a" to 1, "b" to emptyArray<String>()).isComplex(false))
        assertTrue(arrayOf("a", emptyMap<String, String>()).isComplex(false))
        assertTrue(listOf(1, emptyMap<String, String>()).isComplex(false))
        assertTrue(mapOf("a" to 1, "b" to emptyMap<String, String>()).isComplex(false))

    }

    @Test
    fun testHasFile() {
        assertFalse(null.hasFile())
        assertFalse("".hasFile())
        assertFalse("str".hasFile())
        assertFalse(1.hasFile())
        assertFalse(1L.hasFile())
        assertFalse(1.0.hasFile())
        assertFalse(1.0f.hasFile())
        assertTrue(Magics.FILE_STR.hasFile())

        assertFalse(emptyArray<String>().hasFile())
        assertFalse(emptyList<String>().hasFile())
        assertFalse(emptyMap<String, String>().hasFile())
        assertFalse(arrayOf("a").hasFile())
        assertFalse(listOf(1).hasFile())
        assertFalse(mapOf("a" to 1).hasFile())
        assertFalse(arrayOf("a", emptyList<String>()).hasFile())
        assertFalse(listOf(1, emptyList<String>()).hasFile())
        assertFalse(mapOf("a" to 1, "b" to emptyArray<String>()).hasFile())
        assertFalse(arrayOf("a", emptyMap<String, String>()).hasFile())
        assertFalse(listOf(1, emptyMap<String, String>()).hasFile())
        assertFalse(mapOf("a" to 1, "b" to emptyMap<String, String>()).hasFile())

        assertTrue(arrayOf(Magics.FILE_STR).hasFile())
        assertTrue(listOf(Magics.FILE_STR).hasFile())
        assertTrue(mapOf("a" to Magics.FILE_STR).hasFile())
        assertTrue(listOf(mapOf("@x" to 1, "a" to Magics.FILE_STR)).hasFile())

    }
}