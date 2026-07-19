package com.itangcent.easyapi.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for [TokenSizeUtils]. Covers the AI-specific preset
 * list and the decimal (x1000) parser.
 */
class TokenSizeUtilsTest {

    // --- presets list ---

    @Test
    fun testPresetsAreSimpleShorthandStrings() {
        assertEquals(
            listOf("8k", "16k", "32k", "64k", "128k", "200k", "500k", "1m", "2m"),
            TokenSizeUtils.presets
        )
    }

    @Test
    fun testPresetsHaveUniqueLabels() {
        assertEquals(
            "preset labels must be unique",
            TokenSizeUtils.presets.toSet().size,
            TokenSizeUtils.presets.size
        )
    }

    @Test
    fun testPresetsHaveNonBlankLabels() {
        TokenSizeUtils.presets.forEach { label ->
            assertTrue("label must be non-blank: '$label'", label.isNotBlank())
        }
    }

    // --- parse(): bare numbers ---

    @Test
    fun testParseEmptyStringReturnsZero() {
        assertEquals(0, TokenSizeUtils.parse(""))
    }

    @Test
    fun testParseBlankStringReturnsZero() {
        assertEquals(0, TokenSizeUtils.parse("   "))
    }

    @Test
    fun testParsePlainNumber() {
        assertEquals(8000, TokenSizeUtils.parse("8000"))
    }

    @Test
    fun testParsePlainNumberWithSurroundingWhitespace() {
        assertEquals(128_000, TokenSizeUtils.parse("  128000  "))
    }

    @Test
    fun testParseCommaGroupedNumber() {
        assertEquals(8_000, TokenSizeUtils.parse("8,000"))
        assertEquals(1_000_000, TokenSizeUtils.parse("1,000,000"))
    }

    // --- parse(): k/m shorthand (decimal multipliers, x1000) ---

    @Test
    fun testParseLowercaseKShorthand() {
        assertEquals(8_000, TokenSizeUtils.parse("8k"))
        assertEquals(128_000, TokenSizeUtils.parse("128k"))
    }

    @Test
    fun testParseUppercaseKShorthand() {
        assertEquals(8_000, TokenSizeUtils.parse("8K"))
        assertEquals(128_000, TokenSizeUtils.parse("128K"))
    }

    @Test
    fun testParseLowercaseMShorthand() {
        assertEquals(1_000_000, TokenSizeUtils.parse("1m"))
        assertEquals(2_000_000, TokenSizeUtils.parse("2m"))
    }

    @Test
    fun testParseUppercaseMShorthand() {
        assertEquals(1_000_000, TokenSizeUtils.parse("1M"))
    }

    @Test
    fun testParseShorthandWithWhitespace() {
        assertEquals(8_000, TokenSizeUtils.parse("  8k  "))
    }

    // --- parse(): labels (e.g. "8k (8,000)") — first token only ---

    @Test
    fun testParseLabelK() {
        assertEquals(8_000, TokenSizeUtils.parse("8k (8,000)"))
        assertEquals(200_000, TokenSizeUtils.parse("200k (200,000)"))
    }

    @Test
    fun testParseLabelM() {
        assertEquals(1_000_000, TokenSizeUtils.parse("1M (1,000,000)"))
    }

    // --- parse(): invalid input ---

    @Test
    fun testParseNonNumericReturnsZero() {
        assertEquals(0, TokenSizeUtils.parse("abc"))
    }

    @Test
    fun testParseKShorthandWithoutNumberReturnsZero() {
        assertEquals(0, TokenSizeUtils.parse("k"))
    }

    @Test
    fun testParseMShorthandWithoutNumberReturnsZero() {
        assertEquals(0, TokenSizeUtils.parse("m"))
    }

    @Test
    fun testParseNegativeNumberIsPassedThrough() {
        // Negative context windows are nonsensical, but the parser does not
        // clamp — callers own clamping.
        assertEquals(-1, TokenSizeUtils.parse("-1"))
    }

    @Test
    fun testParseMixedAlphaSuffixReturnsZero() {
        assertEquals(0, TokenSizeUtils.parse("8kk"))
        assertEquals(0, TokenSizeUtils.parse("8km"))
    }

    // --- round-trip: parse(preset) is positive for every preset ---

    @Test
    fun testParseEveryPresetResolvesToPositiveTokens() {
        TokenSizeUtils.presets.forEach { label ->
            val tokens = TokenSizeUtils.parse(label)
            assertTrue("parse('$label') should be positive, got $tokens", tokens > 0)
        }
    }
}
