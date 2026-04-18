package com.itangcent.easyapi.rule.parser

import org.junit.Assert.*
import org.junit.Test

class AnnotationExpressionParserPureTest {

    private val parser = AnnotationExpressionParser()

    @Test
    fun testCanParse_withAtPrefix() {
        assertTrue(parser.canParse("@RequestMapping"))
        assertTrue(parser.canParse("@org.springframework.web.bind.annotation.RequestMapping"))
        assertTrue(parser.canParse("@RequestMapping#path"))
        assertTrue(parser.canParse("@"))
    }

    @Test
    fun testCanParse_withoutAtPrefix() {
        assertFalse(parser.canParse("RequestMapping"))
        assertFalse(parser.canParse("#tag"))
        assertFalse(parser.canParse("!expression"))
        assertFalse(parser.canParse(""))
        assertFalse(parser.canParse("\$class:Foo"))
    }
}

class NegationParserPureTest {

    private val parser = NegationParser()

    @Test
    fun testCanParse_withExclamation() {
        assertTrue(parser.canParse("!expression"))
        assertTrue(parser.canParse("!true"))
        assertTrue(parser.canParse("!@Annotation"))
        assertTrue(parser.canParse("!"))
        assertTrue(parser.canParse("  !expression"))
    }

    @Test
    fun testCanParse_withoutExclamation() {
        assertFalse(parser.canParse("expression"))
        assertFalse(parser.canParse("@Annotation"))
        assertFalse(parser.canParse("#tag"))
        assertFalse(parser.canParse(""))
    }
}
