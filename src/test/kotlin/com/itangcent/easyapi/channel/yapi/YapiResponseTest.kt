package com.itangcent.easyapi.channel.yapi

import com.itangcent.easyapi.channel.yapi.model.YapiResponse
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [YapiResponse].
 */
class YapiResponseTest {

    @Test
    fun `success carries data and isSuccess is true`() {
        val r = YapiResponse.success("hello")
        assertTrue(r.isSuccess)
        assertEquals("hello", r.getOrNull())
        assertNull(r.errorMessage())
    }

    @Test
    fun `failure carries error and isSuccess is false`() {
        val r = YapiResponse.failure<String>("something went wrong")
        assertFalse(r.isSuccess)
        assertNull(r.getOrNull())
        assertEquals("something went wrong", r.errorMessage())
    }

    @Test
    fun `success with null data is still considered success`() {
        val r = YapiResponse.success<String?>(null)
        assertTrue(r.isSuccess)
        assertNull(r.getOrNull())
    }

    @Test
    fun `data class equality works`() {
        assertEquals(YapiResponse.success(42), YapiResponse.success(42))
        assertEquals(YapiResponse.failure<Int>("err"), YapiResponse.failure<Int>("err"))
        assertNotEquals(YapiResponse.success(1), YapiResponse.success(2))
    }

    @Test
    fun `rejection is a failure that YApi answered`() {
        val r = YapiResponse.rejection<String>("token 无效")
        assertFalse(r.isSuccess)
        assertNull(r.getOrNull())
        assertEquals("token 无效", r.errorMessage())
        assertTrue(r.rejected)
    }

    @Test
    fun `success and failure are not rejections`() {
        assertFalse(YapiResponse.success("hello").rejected)
        assertFalse(YapiResponse.failure<String>("Request to YApi server failed").rejected)
    }

    @Test
    fun `a rejection is not equal to a failure with the same message`() {
        assertNotEquals(YapiResponse.failure<Int>("err"), YapiResponse.rejection<Int>("err"))
    }
}
