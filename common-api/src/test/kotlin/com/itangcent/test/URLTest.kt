package com.itangcent.test

import com.itangcent.common.model.URL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class URLTest {

    @Test
    fun testURLConcatAndUnion() {
        val nil = URL.of()
        val url1 = URL.of("/a")
        val url2 = URL.of("/b")
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
        val url2 = URL.of("/b")
        val url3 = URL.of("/c", "/d")
        assertEquals("/x", nil.map { "$it/x" }.toString())
        assertEquals("/a/x", url1.map { "$it/x" }.toString())
        assertEquals("/b/x", url2.map { "$it/x" }.toString())
        assertEquals("/c/x,/d/x", url3.map { "$it/x" }.toString())
    }
}