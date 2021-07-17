package com.itangcent.test

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Test case of [ResultLoader]
 */
internal class ResultLoaderTest {

    @Test
    fun testLoad() {
        assertEquals("asdfghjkl", ResultLoader.load())
        assertEquals("123456789", ResultLoader.load("sub"))
    }
}