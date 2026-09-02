package com.itangcent.easyapi.core.util.text

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [KeyValueLineParser].
 */
class KeyValueLineParserTest {

    // ── splitKeyValue ──

    @Test
    fun testSplitKeyValueWithEquals() {
        val result = KeyValueLineParser.splitKeyValue("api.name=test-api")
        assertEquals("api.name" to "test-api", result)
    }

    @Test
    fun testSplitKeyValueWithColon() {
        val result = KeyValueLineParser.splitKeyValue("api.name:test-api")
        assertEquals("api.name" to "test-api", result)
    }

    @Test
    fun testSplitKeyValueUsesFirstSeparator() {
        // The first top-level separator wins; a later '=' becomes part of the value.
        val result = KeyValueLineParser.splitKeyValue("key=a=b")
        assertEquals("key" to "a=b", result)
    }

    @Test
    fun testSplitKeyValueEqualsBeforeColonWins() {
        val result = KeyValueLineParser.splitKeyValue("key=host:port")
        assertEquals("key" to "host:port", result)
    }

    @Test
    fun testSplitKeyValueColonBeforeEqualsWins() {
        val result = KeyValueLineParser.splitKeyValue("key:a=b")
        assertEquals("key" to "a=b", result)
    }

    @Test
    fun testSplitKeyValueTrimsKeyAndValue() {
        val result = KeyValueLineParser.splitKeyValue("  key  =  value  ")
        assertEquals("key" to "value", result)
    }

    @Test
    fun testSplitKeyValueEmptyValue() {
        val result = KeyValueLineParser.splitKeyValue("key=")
        assertEquals("key" to "", result)
    }

    @Test
    fun testSplitKeyValueSeparatorInsideBracketsIgnored() {
        val result = KeyValueLineParser.splitKeyValue("rule[#regex:some.Type]=replacement")
        assertEquals("rule[#regex:some.Type]" to "replacement", result)
    }

    @Test
    fun testSplitKeyValueMultipleSeparatorsInsideBracketsIgnored() {
        val result = KeyValueLineParser.splitKeyValue("rule[a:b:c]=value")
        assertEquals("rule[a:b:c]" to "value", result)
    }

    @Test
    fun testSplitKeyValueNestedBrackets() {
        val result = KeyValueLineParser.splitKeyValue("rule[a[b:c]d]=value")
        assertEquals("rule[a[b:c]d]" to "value", result)
    }

    @Test
    fun testSplitKeyValueNoSeparatorReturnsNull() {
        assertNull(KeyValueLineParser.splitKeyValue("just-a-key"))
    }

    @Test
    fun testSplitKeyValueOnlySeparatorInsideBracketsReturnsNull() {
        // The only separators are nested in brackets, so there is no top-level split.
        assertNull(KeyValueLineParser.splitKeyValue("rule[a:b]"))
    }

    @Test
    fun testSplitKeyValueSeparatorAtStartReturnsNull() {
        // i > 0 guard: a leading separator does not produce an empty key split.
        assertNull(KeyValueLineParser.splitKeyValue("=value"))
    }

    @Test
    fun testSplitKeyValueBlankKeyReturnsNull() {
        assertNull(KeyValueLineParser.splitKeyValue("   =value"))
    }

    @Test
    fun testSplitKeyValueEmptyStringReturnsNull() {
        assertNull(KeyValueLineParser.splitKeyValue(""))
    }

    @Test
    fun testSplitKeyValueUnbalancedClosingBracket() {
        // Stray ']' keeps bracketDepth at 0, so the '=' is a valid top-level separator.
        val result = KeyValueLineParser.splitKeyValue("key]=value")
        assertEquals("key]" to "value", result)
    }
}
