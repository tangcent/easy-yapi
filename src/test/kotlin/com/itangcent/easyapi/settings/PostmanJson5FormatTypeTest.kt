package com.itangcent.easyapi.settings

import org.junit.Assert.*
import org.junit.Test

class PostmanJson5FormatTypeTest {

    @Test
    fun testValues() {
        val values = PostmanJson5FormatType.values()
        assertEquals(5, values.size)
    }

    @Test
    fun testDesc() {
        assertEquals("not use json5 anywhere", PostmanJson5FormatType.NONE.desc)
        assertEquals("for request only", PostmanJson5FormatType.REQUEST_ONLY.desc)
        assertEquals("for response only", PostmanJson5FormatType.RESPONSE_ONLY.desc)
        assertEquals("for example only", PostmanJson5FormatType.EXAMPLE_ONLY.desc)
        assertEquals("always use json5", PostmanJson5FormatType.ALL.desc)
    }

    @Test
    fun testNone_neverUsesJson5() {
        assertFalse(PostmanJson5FormatType.NONE.needUseJson5(1))
        assertFalse(PostmanJson5FormatType.NONE.needUseJson5(4))
        assertFalse(PostmanJson5FormatType.NONE.needUseJson5(8))
    }

    @Test
    fun testRequestOnly() {
        assertTrue(PostmanJson5FormatType.REQUEST_ONLY.needUseJson5(1))
        assertTrue(PostmanJson5FormatType.REQUEST_ONLY.needUseJson5(4))
        assertFalse(PostmanJson5FormatType.REQUEST_ONLY.needUseJson5(8))
    }

    @Test
    fun testResponseOnly() {
        assertFalse(PostmanJson5FormatType.RESPONSE_ONLY.needUseJson5(1))
        assertTrue(PostmanJson5FormatType.RESPONSE_ONLY.needUseJson5(8))
    }

    @Test
    fun testExampleOnly() {
        assertFalse(PostmanJson5FormatType.EXAMPLE_ONLY.needUseJson5(1))
        assertTrue(PostmanJson5FormatType.EXAMPLE_ONLY.needUseJson5(4))
        assertTrue(PostmanJson5FormatType.EXAMPLE_ONLY.needUseJson5(8))
    }

    @Test
    fun testAll_alwaysUsesJson5() {
        assertTrue(PostmanJson5FormatType.ALL.needUseJson5(1))
        assertTrue(PostmanJson5FormatType.ALL.needUseJson5(4))
        assertTrue(PostmanJson5FormatType.ALL.needUseJson5(8))
    }

    @Test
    fun testValueOf() {
        assertEquals(PostmanJson5FormatType.NONE, PostmanJson5FormatType.valueOf("NONE"))
        assertEquals(PostmanJson5FormatType.ALL, PostmanJson5FormatType.valueOf("ALL"))
        assertEquals(PostmanJson5FormatType.EXAMPLE_ONLY, PostmanJson5FormatType.valueOf("EXAMPLE_ONLY"))
    }
}
