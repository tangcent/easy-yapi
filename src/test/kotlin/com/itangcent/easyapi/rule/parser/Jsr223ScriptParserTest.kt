package com.itangcent.easyapi.rule.parser

import org.junit.Assert.*
import org.junit.Test

class Jsr223ScriptParserTest {

    @Test
    fun testGroovyScriptParserCanParse() {
        val parser = GroovyScriptParser()
        assertTrue("Should parse groovy: prefix", parser.canParse("groovy: it.name()"))
        assertFalse("Should not parse non-groovy prefix", parser.canParse("javascript: code"))
        assertFalse("Should not parse empty string", parser.canParse(""))
    }

    @Test
    fun testGroovyScriptParserPrefix() {
        val parser = GroovyScriptParser()
        assertTrue("Should recognize groovy: prefix", parser.canParse("groovy:1+1"))
    }
}
