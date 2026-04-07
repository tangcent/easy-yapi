package com.itangcent.easyapi.rule.parser

import org.junit.Assert.*
import org.junit.Test

class TagExpressionParserTest {

    private val parser = TagExpressionParser()

    @Test
    fun testCanParse_withHashPrefix() {
        assertTrue(parser.canParse("#tag"))
        assertTrue(parser.canParse("#mock"))
        assertTrue(parser.canParse("#deprecated"))
        assertTrue(parser.canParse("#"))
    }

    @Test
    fun testCanParse_notRegex() {
        assertFalse(parser.canParse("#regex:.*"))
        assertFalse(parser.canParse("#regex:"))
    }

    @Test
    fun testCanParse_withoutHashPrefix() {
        assertFalse(parser.canParse("tag"))
        assertFalse(parser.canParse("@Annotation"))
        assertFalse(parser.canParse(""))
        assertFalse(parser.canParse("!expression"))
    }
}
