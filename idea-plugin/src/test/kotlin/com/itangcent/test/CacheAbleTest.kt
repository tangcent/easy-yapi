package com.itangcent.test

import com.itangcent.intellij.util.CacheAble
import com.itangcent.intellij.util.DefaultCacheAle
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import org.junit.runners.JUnit4


/**
 * Test case for [CacheAble]
 */
@RunWith(JUnit4::class)
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