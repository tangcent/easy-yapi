package com.itangcent.http

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Test case of [BasicHttpParam]
 */
internal class BasicHttpParamTest {

    @Test
    fun testHttpParam() {
        val param = BasicHttpParam("name", "value", "file")
        assertEquals("name", param.name())
        assertEquals("value", param.value())
        assertEquals("file", param.type())

        val param2 = BasicHttpParam("name", "value")
        assertEquals("name", param2.name())
        assertEquals("value", param2.value())
        assertEquals("text", param2.type())
    }

    @Test
    fun testSetters() {
        val param = BasicHttpParam()
        param.setName("name")
        param.setValue("value")
        param.setType("file")
        assertEquals("name", param.name())
        assertEquals("value", param.value())
        assertEquals("file", param.type())
    }
}