package com.itangcent.easyapi.exporter.feign

import org.junit.Assert.*
import org.junit.Test

class FeignClientInfoTest {

    @Test
    fun testFeignClientInfoCreation() {
        val info = FeignClientInfo(
            path = "/api",
            url = "http://example.com",
            name = "userService"
        )
        assertEquals("/api", info.path)
        assertEquals("http://example.com", info.url)
        assertEquals("userService", info.name)
    }

    @Test
    fun testFeignClientInfoWithNullValues() {
        val info = FeignClientInfo()
        assertNull(info.path)
        assertNull(info.url)
        assertNull(info.name)
    }

    @Test
    fun testFeignClientInfoWithOnlyPath() {
        val info = FeignClientInfo(path = "/api")
        assertEquals("/api", info.path)
        assertNull(info.url)
        assertNull(info.name)
    }

    @Test
    fun testFeignClientInfoWithOnlyUrl() {
        val info = FeignClientInfo(url = "http://example.com")
        assertNull(info.path)
        assertEquals("http://example.com", info.url)
        assertNull(info.name)
    }

    @Test
    fun testFeignClientInfoWithOnlyName() {
        val info = FeignClientInfo(name = "userService")
        assertNull(info.path)
        assertNull(info.url)
        assertEquals("userService", info.name)
    }

    @Test
    fun testFeignClientInfoEquality() {
        val info1 = FeignClientInfo(path = "/api", url = "http://example.com", name = "service")
        val info2 = FeignClientInfo(path = "/api", url = "http://example.com", name = "service")
        assertEquals(info1, info2)
    }

    @Test
    fun testFeignClientInfoCopy() {
        val original = FeignClientInfo(path = "/api", url = "http://example.com", name = "service")
        val copy = original.copy(path = "/v2/api")
        assertEquals("/v2/api", copy.path)
        assertEquals("http://example.com", copy.url)
        assertEquals("/api", original.path)
    }

    @Test
    fun testFeignClientInfoComponentFunctions() {
        val info = FeignClientInfo(path = "/api", url = "http://example.com", name = "service")
        val (path, url, name) = info
        assertEquals("/api", path)
        assertEquals("http://example.com", url)
        assertEquals("service", name)
    }
}
