package com.itangcent.easyapi.settings

import org.junit.Assert.*
import org.junit.Test

class HttpClientTypeTest {

    @Test
    fun testValues() {
        val values = HttpClientType.values()
        assertEquals(2, values.size)
    }

    @Test
    fun testValue() {
        assertEquals("Apache", HttpClientType.APACHE.value)
        assertEquals("Default", HttpClientType.DEFAULT.value)
    }

    @Test
    fun testName() {
        assertEquals("APACHE", HttpClientType.APACHE.name)
        assertEquals("DEFAULT", HttpClientType.DEFAULT.name)
    }

    @Test
    fun testValueOf() {
        assertEquals(HttpClientType.APACHE, HttpClientType.valueOf("APACHE"))
        assertEquals(HttpClientType.DEFAULT, HttpClientType.valueOf("DEFAULT"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testValueOf_invalid() {
        HttpClientType.valueOf("INVALID")
    }
}
