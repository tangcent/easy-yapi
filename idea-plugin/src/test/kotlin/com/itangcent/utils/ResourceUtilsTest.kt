package com.itangcent.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


/**
 * Test case for [ResourceUtils]
 */
class ResourceUtilsTest {

    @Test
    fun testReadResource() {
        assertEquals("token=111111", ResourceUtils.readResource("demo.properties"))
        assertEquals("", ResourceUtils.readResource("demo-not-existed.properties"))
    }
}