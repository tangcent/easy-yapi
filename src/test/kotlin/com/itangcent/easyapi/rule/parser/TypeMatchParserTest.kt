package com.itangcent.easyapi.rule.parser

import org.junit.Assert.*
import org.junit.Test

class TypeMatchParserTest {

    private val parser = TypeMatchParser()

    @Test
    fun testCanParse_qualifiedName() {
        assertTrue(parser.canParse("java.lang.String"))
        assertTrue(parser.canParse("com.example.User"))
        assertTrue(parser.canParse("java.util.List"))
    }

    @Test
    fun testCanParse_withoutDot() {
        assertFalse(parser.canParse("String"))
        assertFalse(parser.canParse("int"))
        assertFalse(parser.canParse(""))
    }

    @Test
    fun testCanParse_excludesAnnotation() {
        assertFalse(parser.canParse("@com.example.Annotation"))
    }

    @Test
    fun testCanParse_excludesTag() {
        assertFalse(parser.canParse("#some.tag"))
    }

    @Test
    fun testCanParse_excludesJs() {
        assertFalse(parser.canParse("js:some.code"))
    }

    @Test
    fun testCanParse_excludesGroovy() {
        assertFalse(parser.canParse("groovy:some.code"))
    }
}

class ClassMatchParserTest {

    private val parser = ClassMatchParser()

    @Test
    fun testCanParse_withPrefix() {
        assertTrue(parser.canParse("\$class:com.example.Foo"))
        assertTrue(parser.canParse("\$class:? extend com.example.Foo"))
        assertTrue(parser.canParse("\$class:"))
    }

    @Test
    fun testCanParse_withoutPrefix() {
        assertFalse(parser.canParse("com.example.Foo"))
        assertFalse(parser.canParse("@Annotation"))
        assertFalse(parser.canParse("#tag"))
        assertFalse(parser.canParse(""))
    }

    @Test
    fun testCanParse_withWhitespace() {
        assertTrue(parser.canParse("  \$class:com.example.Foo"))
    }
}
