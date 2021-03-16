package com.itangcent.utils

import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import org.junit.runners.JUnit4


/**
 * Test case for [ResourceUtils]
 */
@RunWith(JUnit4::class)
class ResourceUtilsTest {

    @Test
    fun testReadResource() {
        assertEquals("token=111111", ResourceUtils.readResource("demo.properties"))
        assertEquals("", ResourceUtils.readResource("demo-not-existed.properties"))
    }
}