package com.itangcent.idea.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


/**
 * Test case for [GsonExUtils]
 */
class GsonExUtilsTest {

    @Test
    fun `resolveGsonLazily replaces LazilyParsedNumber with Integer when present`() {
        val originalString = "This is a test with \"com.google.gson.internal.LazilyParsedNumber\" included"
        val expected = "This is a test with \"java.lang.Integer\" included"
        val result = GsonExUtils.resolveGsonLazily(originalString)
        assertEquals(expected, result)
    }

    @Test
    fun `resolveGsonLazily returns the original string when LazilyParsedNumber is not present`() {
        val originalString = "This is a test without the specific substring"
        val result = GsonExUtils.resolveGsonLazily(originalString)
        assertEquals(originalString, result)
    }

    @Test
    fun `resolveGsonLazily returns an empty string when given an empty string`() {
        val originalString = ""
        val result = GsonExUtils.resolveGsonLazily(originalString)
        assertEquals(originalString, result)
    }

    @Test
    fun `resolveGsonLazily replaces the entire string when it only contains LazilyParsedNumber`() {
        val originalString = "\"com.google.gson.internal.LazilyParsedNumber\""
        val expected = "\"java.lang.Integer\""
        val result = GsonExUtils.resolveGsonLazily(originalString)
        assertEquals(expected, result)
    }
}