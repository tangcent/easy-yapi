package com.itangcent.easyapi.core.util.text

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure unit tests for [ByteSizeUtil]. No IntelliJ fixture needed — the
 * helper is a pure function with no Swing/PSI dependency.
 */
class ByteSizeUtilTest {

    // --- parse(): bare numbers ---

    @Test
    fun testParseEmptyStringReturnsZero() {
        assertEquals(0L, ByteSizeUtil.parse(""))
    }

    @Test
    fun testParseBlankStringReturnsZero() {
        assertEquals(0L, ByteSizeUtil.parse("   "))
    }

    @Test
    fun testParseZeroReturnsZero() {
        assertEquals(0L, ByteSizeUtil.parse("0"))
    }

    @Test
    fun testParsePlainNumber() {
        assertEquals(1024L, ByteSizeUtil.parse("1024"))
    }

    @Test
    fun testParsePlainNumberWithSurroundingWhitespace() {
        assertEquals(128_000L, ByteSizeUtil.parse("  128000  "))
    }

    @Test
    fun testParseCommaGroupedNumber() {
        assertEquals(1_024L, ByteSizeUtil.parse("1,024"))
        assertEquals(1_048_576L, ByteSizeUtil.parse("1,048,576"))
    }

    // --- parse(): k/kb shorthand (binary multipliers, x1024) ---

    @Test
    fun testParseLowercaseKShorthand() {
        assertEquals(1024L, ByteSizeUtil.parse("1k"))
        assertEquals(8192L, ByteSizeUtil.parse("8k"))
    }

    @Test
    fun testParseUppercaseKShorthand() {
        assertEquals(1024L, ByteSizeUtil.parse("1K"))
        assertEquals(8192L, ByteSizeUtil.parse("8K"))
    }

    @Test
    fun testParseLowercaseKbShorthand() {
        assertEquals(1024L, ByteSizeUtil.parse("1kb"))
        assertEquals(8192L, ByteSizeUtil.parse("8kb"))
    }

    @Test
    fun testParseUppercaseKbShorthand() {
        assertEquals(1024L, ByteSizeUtil.parse("1KB"))
        assertEquals(8192L, ByteSizeUtil.parse("8KB"))
    }

    // --- parse(): m/mb shorthand (binary multipliers) ---

    @Test
    fun testParseLowercaseMShorthand() {
        assertEquals(1_048_576L, ByteSizeUtil.parse("1m"))
    }

    @Test
    fun testParseUppercaseMShorthand() {
        assertEquals(1_048_576L, ByteSizeUtil.parse("1M"))
    }

    @Test
    fun testParseMbShorthand() {
        assertEquals(1_048_576L, ByteSizeUtil.parse("1MB"))
        assertEquals(1_048_576L, ByteSizeUtil.parse("1mb"))
    }

    // --- parse(): g/gb shorthand (binary multipliers) ---

    @Test
    fun testParseLowercaseGShorthand() {
        assertEquals(1_073_741_824L, ByteSizeUtil.parse("1g"))
    }

    @Test
    fun testParseUppercaseGShorthand() {
        assertEquals(1_073_741_824L, ByteSizeUtil.parse("1G"))
    }

    @Test
    fun testParseGbShorthand() {
        assertEquals(1_073_741_824L, ByteSizeUtil.parse("1GB"))
        assertEquals(1_073_741_824L, ByteSizeUtil.parse("1gb"))
    }

    // --- parse(): whitespace between number and unit ---

    @Test
    fun testParseShorthandWithSpace() {
        assertEquals(1024L, ByteSizeUtil.parse("1 KB"))
        assertEquals(1_048_576L, ByteSizeUtil.parse("1 MB"))
    }

    @Test
    fun testParseDecimalFormattedInput() {
        // format() produces decimals like "1.5 KB" — parse should round-trip.
        assertEquals(1536L, ByteSizeUtil.parse("1.5 KB"))
        assertEquals(1024L, ByteSizeUtil.parse("1.0 KB"))
    }

    @Test
    fun testParseShorthandWithSurroundingWhitespace() {
        assertEquals(8192L, ByteSizeUtil.parse("  8k  "))
    }

    // --- parse(): invalid input ---

    @Test
    fun testParseNonNumericReturnsZero() {
        assertEquals(0L, ByteSizeUtil.parse("abc"))
    }

    @Test
    fun testParseKShorthandWithoutNumberReturnsZero() {
        assertEquals(0L, ByteSizeUtil.parse("k"))
        assertEquals(0L, ByteSizeUtil.parse("kb"))
    }

    @Test
    fun testParseMShorthandWithoutNumberReturnsZero() {
        assertEquals(0L, ByteSizeUtil.parse("m"))
        assertEquals(0L, ByteSizeUtil.parse("mb"))
    }

    @Test
    fun testParseNegativeNumberIsPassedThrough() {
        assertEquals(-1L, ByteSizeUtil.parse("-1"))
    }

    @Test
    fun testParseMixedAlphaSuffixReturnsZero() {
        assertEquals(0L, ByteSizeUtil.parse("8kk"))
        assertEquals(0L, ByteSizeUtil.parse("8km"))
    }

    // --- format() ---

    @Test
    fun testFormatZeroBytes() {
        assertEquals("0 B", ByteSizeUtil.format(0))
    }

    @Test
    fun testFormatBytesUnderOneKb() {
        assertEquals("1 B", ByteSizeUtil.format(1))
        assertEquals("512 B", ByteSizeUtil.format(512))
        assertEquals("1023 B", ByteSizeUtil.format(1023))
    }

    @Test
    fun testFormatKilobytes() {
        assertEquals("1.0 KB", ByteSizeUtil.format(1024))
        assertEquals("1.5 KB", ByteSizeUtil.format(1536))
        assertEquals("512.0 KB", ByteSizeUtil.format(512L * 1024))
    }

    @Test
    fun testFormatMegabytes() {
        val oneMb = 1024L * 1024
        assertEquals("1.0 MB", ByteSizeUtil.format(oneMb))
        assertEquals("2.5 MB", ByteSizeUtil.format(oneMb * 5 / 2))
    }

    @Test
    fun testFormatGigabytes() {
        val oneGb = 1024L * 1024 * 1024
        assertEquals("1.0 GB", ByteSizeUtil.format(oneGb))
        assertEquals("2.0 GB", ByteSizeUtil.format(oneGb * 2))
    }

    // --- round-trip: parse(format(x)) == x for representative sizes ---

    @Test
    fun testParseFormatRoundTrip() {
        val sizes = listOf(0L, 512L, 1024L, 1536L, 1024L * 1024, 2L * 1024 * 1024, 1024L * 1024 * 1024)
        sizes.forEach { size ->
            val formatted = ByteSizeUtil.format(size)
            val parsed = ByteSizeUtil.parse(formatted)
            assertEquals("parse(format($size)) should round-trip (formatted='$formatted')", size, parsed)
        }
    }
}
