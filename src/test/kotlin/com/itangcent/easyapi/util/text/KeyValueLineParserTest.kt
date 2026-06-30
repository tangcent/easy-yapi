package com.itangcent.easyapi.util.text

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

    // ── splitKeyFilterValue ──

    @Test
    fun testSplitKeyFilterValueWithFilter() {
        val result = KeyValueLineParser.splitKeyFilterValue("key[filter]=value")
        assertEquals(Triple("key", "filter", "value"), result)
    }

    @Test
    fun testSplitKeyFilterValueWithoutFilter() {
        val result = KeyValueLineParser.splitKeyFilterValue("key=value")
        assertEquals(Triple("key", null, "value"), result)
    }

    @Test
    fun testSplitKeyFilterValueColonSeparator() {
        val result = KeyValueLineParser.splitKeyFilterValue("key[filter]:value")
        assertEquals(Triple("key", "filter", "value"), result)
    }

    @Test
    fun testSplitKeyFilterValueFilterContainingSeparators() {
        val result = KeyValueLineParser.splitKeyFilterValue("json.rule.convert[#regex:some.Type]=replacement")
        assertEquals(Triple("json.rule.convert", "#regex:some.Type", "replacement"), result)
    }

    @Test
    fun testSplitKeyFilterValueUnterminatedBracketSwallowsSeparator() {
        // The '[' is never closed, so the '=' is treated as nested inside the
        // bracket and no top-level separator is found -> null.
        assertNull(KeyValueLineParser.splitKeyFilterValue("key[filter=value"))
    }

    @Test
    fun testSplitKeyFilterValueBracketNotAtEndTreatedAsKey() {
        // Left-hand side contains a closed '[...]' but does not end with ']',
        // so the whole left-hand side is returned as the key.
        val result = KeyValueLineParser.splitKeyFilterValue("key[filter]suffix=value")
        assertEquals(Triple("key[filter]suffix", null, "value"), result)
    }

    @Test
    fun testSplitKeyFilterValueEmptyKeyTreatedAsKey() {
        // Empty key before '[' → whole left-hand side returned as key, null filter.
        val result = KeyValueLineParser.splitKeyFilterValue("[filter]=value")
        assertEquals(Triple("[filter]", null, "value"), result)
    }

    @Test
    fun testSplitKeyFilterValueEmptyFilterTreatedAsKey() {
        val result = KeyValueLineParser.splitKeyFilterValue("key[]=value")
        assertEquals(Triple("key[]", null, "value"), result)
    }

    @Test
    fun testSplitKeyFilterValueTrimsKeyAndFilter() {
        val result = KeyValueLineParser.splitKeyFilterValue("key[ filter ]=value")
        assertEquals(Triple("key", "filter", "value"), result)
    }

    @Test
    fun testSplitKeyFilterValueNoSeparatorReturnsNull() {
        assertNull(KeyValueLineParser.splitKeyFilterValue("key[filter]"))
    }

    @Test
    fun testSplitKeyFilterValueEmptyValue() {
        val result = KeyValueLineParser.splitKeyFilterValue("key[filter]=")
        assertEquals(Triple("key", "filter", ""), result)
    }
}
