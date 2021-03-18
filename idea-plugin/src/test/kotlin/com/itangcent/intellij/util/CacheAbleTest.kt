package com.itangcent.intellij.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


/**
 * Test case for [CacheAble]
 */
class CacheAbleTest {

    @Test
    fun testCache() {
        val cacheAble: CacheAble = DefaultCacheAle()

        assertEquals("str", cacheAble.cache("str") { "str" })
        assertEquals("str", cacheAble.cache("str") { })
        assertEquals("str", cacheAble.cache("str") { 1 })

        var cnt = 0
        assertEquals("str", cacheAble.cache("str") { cnt++ })
        assertEquals(0, cnt)

        assertEquals(null, cacheAble.cache<Any>("null") { null })
        assertEquals(null, cacheAble.cache("null") { 1 })
    }
}