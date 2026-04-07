package com.itangcent.easyapi.rule.parser

import org.junit.Assert.*
import org.junit.Test

class RegexParserTest {

    private val parser = RegexParser()

    @Test
    fun testCanParse_withPrefix() {
        assertTrue(parser.canParse("#regex:.*"))
        assertTrue(parser.canParse("#regex:"))
        assertTrue(parser.canParse("#regex:[a-z]+"))
    }

    @Test
    fun testCanParse_withoutPrefix() {
        assertFalse(parser.canParse(".*"))
        assertFalse(parser.canParse("#tag"))
        assertFalse(parser.canParse("@Annotation"))
        assertFalse(parser.canParse(""))
        assertFalse(parser.canParse("regex:.*"))
    }
}
