package com.itangcent.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * Test case for [ResourceUtils]
 */
class ResourceUtilsTest {

    @Test
    fun testReadResource() {
        assertEquals("token=111111", ResourceUtils.readResource("demo.properties"))
        assertEquals("", ResourceUtils.readResource("demo-not-existed.properties"))
    }

    @Test
    fun testFindResource() {
        assertTrue(ResourceUtils.findResource("demo.properties")!!.toString().endsWith("demo.properties"))
        assertNull(ResourceUtils.findResource("demo-not-existed.properties"))
    }
}