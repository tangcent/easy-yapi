package com.itangcent.easyapi.rule.parser

import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.rule.context.RuleContext
import com.itangcent.easyapi.rule.engine.RuleEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NegationParserMockTest {

    private lateinit var parser: NegationParser
    private var ruleEngine: RuleEngine? = null

    @Before
    fun setUp() {
        parser = NegationParser()
        ruleEngine = mock()
        parser.setRuleEngine(ruleEngine!!)
    }

    @Test
    fun testCanParse_withExclamation() {
        assertTrue(parser.canParse("!expression"))
        assertTrue(parser.canParse("!true"))
        assertTrue(parser.canParse("!"))
        assertTrue(parser.canParse("  !expression"))
    }

    @Test
    fun testCannotParseNonNegation() {
        assertFalse(parser.canParse("expression"))
        assertFalse(parser.canParse("@Annotation"))
        assertFalse(parser.canParse(""))
    }

    @Test
    fun testParseNegatesTrue() = runBlocking {
        whenever(ruleEngine!!.parseExpression(any(), any(), any())).thenReturn(true)
        val context = mock<RuleContext>()
        val result = parser.parse("!true_expr", context, null)
        assertEquals("Should negate true to false", false, result)
    }

    @Test
    fun testParseNegatesFalse() = runBlocking {
        whenever(ruleEngine!!.parseExpression(any(), any(), any())).thenReturn(false)
        val context = mock<RuleContext>()
        val result = parser.parse("!false_expr", context, null)
        assertEquals("Should negate false to true", true, result)
    }

    @Test
    fun testParseEmptyInnerReturnsFalse() = runBlocking {
        val context = mock<RuleContext>()
        val result = parser.parse("!", context, null)
        assertEquals("Empty inner should return false", false, result)
    }
}
