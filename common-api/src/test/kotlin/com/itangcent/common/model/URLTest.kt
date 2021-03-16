package com.itangcent.common.model

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class URLTest {

    @Test
    fun testSingle() {
        val nil = URL.of()
        val url1 = URL.of("/a")
        val url2 = URL.of(listOf("/b"))
        val url3 = URL.of("/c", "/d")
        assertTrue(nil.single())
        assertTrue(url1.single())
        assertTrue(url2.single())
        assertFalse(url3.single())
    }

    @Test
    fun testUrl() {
        val nil = URL.of()
        val url1 = URL.of("/a")
        val url2 = URL.of(listOf("/b"))
        val url3 = URL.of("/c", "/d")
        assertEquals(null, nil.url())
        assertEquals("/a", url1.url())
        assertEquals("/b", url2.url())
        assertEquals("/c", url3.url())
    }

    @Test
    fun testUrls() {
        val nil = URL.of()
        val url1 = URL.of("/a")
        val url2 = URL.of(listOf("/b"))
        val url3 = URL.of("/c", "/d")
        assertArrayEquals(emptyArray<String>(), nil.urls())
        assertArrayEquals(arrayOf("/a"), url1.urls())
        assertArrayEquals(arrayOf("/b"), url2.urls())
        assertArrayEquals(arrayOf("/c", "/d"), url3.urls())
    }

    @Test
    fun testURLConcatAndUnion() {
        val nil = URL.of()
        val url1 = URL.of("/a")
        val url2 = URL.of(listOf("/b"))
        val url3 = URL.of("/c", "/d")
        assertEquals("/a", nil.concat(url1).toString())
        assertEquals("/a/b", url1.concat(url2).toString())
        assertEquals("/b/c,/b/d", url2.concat(url3).toString())
        assertEquals("/a", nil.union(url1).toString())
        assertEquals("/a,/b", url1.union(url2).toString())
        assertEquals("/b,/c,/d", url2.union(url3).toString())
    }

    @Test
    fun testURLMap() {
        val nil = URL.of()
        val url1 = URL.of("/a")
        val url2 = URL.of(listOf("/b"))
        val url3 = URL.of("/c", "/d")
        assertEquals("/x", nil.map { "$it/x" }.toString())
        assertEquals("/a/x", url1.map { "$it/x" }.toString())
        assertEquals("/b/x", url2.map { "$it/x" }.toString())
        assertEquals("/c/x,/d/x", url3.map { "$it/x" }.toString())
    }

    @Test
    fun testURLFlatMap() {
        val nil = URL.of()
        val url1 = URL.of("/a")
        val url2 = URL.of(listOf("/b"))
        val url3 = URL.of("/c", "/d")
        assertEquals("/x", nil.flatMap { URL.of("$it/x") }.toString())
        assertEquals("/a/x", url1.flatMap { URL.of("$it/x") }.toString())
        assertEquals("/b/x", url2.flatMap { URL.of("$it/x") }.toString())
        assertEquals("/c/x,/d/x", url3.flatMap { URL.of("$it/x") }.toString())
        assertEquals("/x,/y", nil.flatMap { URL.of("$it/x", "$it/y") }.toString())
        assertEquals("/a/x,/a/y", url1.flatMap { URL.of("$it/x", "$it/y") }.toString())
        assertEquals("/b/x,/b/y", url2.flatMap { URL.of("$it/x", "$it/y") }.toString())
        assertEquals("/c/x,/c/y,/d/x,/d/y", url3.flatMap { URL.of("$it/x", "$it/y") }.toString())
    }
}