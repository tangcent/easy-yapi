package com.itangcent.idea.plugin.utils

import com.google.inject.Inject
import com.itangcent.mock.BaseContextTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test case of [StringDiffHelper]
 */
internal class StringDiffHelperTest : BaseContextTest() {

    @Inject
    private lateinit var stringDiffHelper: StringDiffHelper

    @Test
    fun testDiff() {

        assertEquals(0, stringDiffHelper.diff("", ""))
        assertEquals(100, stringDiffHelper.diff("", "abcdefg"))
        assertEquals(100, stringDiffHelper.diff("abcdefg", ""))
        assertEquals(0, stringDiffHelper.diff("abcdefg", "abcdefg"))
        assertEquals(100, stringDiffHelper.diff("abcdefg", "1234567"))

        val diff1 = stringDiffHelper.diff("abcdefghijkl", "abcdehijkl")
        logger.info("diff of 'abcdefghijkl'&''abcdehijkl' is $diff1")
        assertTrue(diff1 > 0)
        assertTrue(diff1 < 20)

        val diff2 = stringDiffHelper.diff("abcdefghijkl", "jkl123456")
        logger.info("diff of 'abcdefghijkl'&''jkl123456' is $diff2")
        assertTrue(diff2 > 50)
        assertTrue(diff2 < 80)
        assertTrue(diff2 > diff1)
    }

    @Test
    fun testLargeDiff() {

        assertEquals(100, stringDiffHelper.diff("abcdefghijkl".repeat(100),
                "123456789".repeat(100)))

        val diff1 = stringDiffHelper.diff("abcdefghijkl".repeat(100),
                "abcdefghijkl".repeat(10) + "123456789".repeat(90))
        assertTrue(diff1 > 80)
        assertTrue(diff1 < 100)
    }
}