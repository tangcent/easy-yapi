package com.itangcent.http

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Test case of [BasicHttpHeader]
 */
internal class BasicHttpHeaderTest {
    @Test
    fun testNameAndValue() {
        val header = BasicHttpHeader("name", "value")
        assertEquals("name", header.name())
        assertEquals("value", header.value())
    }

    @Test
    fun testSetNameAndValue() {
        val header = BasicHttpHeader()
        header.setName("name")
        header.setValue("value")
        assertEquals("name", header.name())
        assertEquals("value", header.value())
    }
}