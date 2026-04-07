package com.itangcent.easyapi.rule.parser

import com.itangcent.easyapi.rule.RuleKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock

class LiteralParserTest {

    private val parser = LiteralParser()
    private val context = mock<com.itangcent.easyapi.rule.context.RuleContext>()

    @Test
    fun testCanParse_alwaysTrue() {
        assertTrue(parser.canParse("anything"))
        assertTrue(parser.canParse(""))
        assertTrue(parser.canParse("true"))
        assertTrue(parser.canParse("false"))
        assertTrue(parser.canParse("some.rule.key"))
    }

    @Test
    fun testParse_stringKey_returnsLiteral() = runBlocking {
        val key = RuleKey.string("test.key")
        assertEquals("hello", parser.parse("hello", context, key))
        assertEquals("some.value", parser.parse("some.value", context, key))
        // LiteralParser trims the expression
        assertEquals("trimmed", parser.parse("  trimmed  ", context, key))
    }

    @Test
    fun testParse_stringKey_trueString() = runBlocking {
        val key = RuleKey.string("test.key")
        assertEquals("true", parser.parse("true", context, key))
        assertEquals("false", parser.parse("false", context, key))
    }

    @Test
    fun testParse_booleanKey_true() = runBlocking {
        val key = RuleKey.boolean("test.key")
        assertEquals(true, parser.parse("true", context, key))
        assertEquals(true, parser.parse("1", context, key))
        assertEquals(true, parser.parse("  true  ", context, key))
        assertEquals(true, parser.parse("TRUE", context, key))
    }

    @Test
    fun testParse_booleanKey_false() = runBlocking {
        val key = RuleKey.boolean("test.key")
        assertEquals(false, parser.parse("false", context, key))
        assertEquals(false, parser.parse("0", context, key))
        assertEquals(false, parser.parse("  false  ", context, key))
        assertEquals(false, parser.parse("FALSE", context, key))
    }

    @Test
    fun testParse_booleanKey_unknownValue() = runBlocking {
        val key = RuleKey.boolean("test.key")
        assertNull(parser.parse("maybe", context, key))
        assertNull(parser.parse("yes", context, key))
        assertNull(parser.parse("no", context, key))
    }

    @Test
    fun testParse_nullKey_returnsLiteral() = runBlocking {
        assertEquals("hello", parser.parse("hello", context, null))
        assertEquals("true", parser.parse("true", context, null))
    }

}
