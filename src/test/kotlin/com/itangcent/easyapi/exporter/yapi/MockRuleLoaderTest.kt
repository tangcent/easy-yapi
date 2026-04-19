package com.itangcent.easyapi.exporter.yapi

import org.junit.Assert.*
import org.junit.Test

class MockRuleLoaderTest {

    @Test
    fun testParseMockRuleValid() {
        val result = MockRuleLoader.parseMockRule("*.email|string=@email")
        assertNotNull(result)
        assertEquals("*.email|string", result!!.first)
        assertEquals("@email", result.second)
    }

    @Test
    fun testParseMockRuleWithSpaces() {
        val result = MockRuleLoader.parseMockRule("  *.email|string  =  @email  ")
        assertNotNull(result)
        assertEquals("*.email|string", result!!.first)
        assertEquals("@email", result.second)
    }

    @Test
    fun testParseMockRuleSimplePattern() {
        val result = MockRuleLoader.parseMockRule("email=@email")
        assertNotNull(result)
        assertEquals("email", result!!.first)
        assertEquals("@email", result.second)
    }

    @Test
    fun testParseMockRuleWithEqualsInValue() {
        val result = MockRuleLoader.parseMockRule("field.mock=groovy:\"@integer(\"+it.ann(\"Max\")+\")\"")
        assertNotNull(result)
        assertEquals("field.mock", result!!.first)
        assertEquals("groovy:\"@integer(\"+it.ann(\"Max\")+\")\"", result.second)
    }

    @Test
    fun testParseMockRuleEmptyLine() {
        assertNull(MockRuleLoader.parseMockRule(""))
    }

    @Test
    fun testParseMockRuleBlankLine() {
        assertNull(MockRuleLoader.parseMockRule("   "))
    }

    @Test
    fun testParseMockRuleComment() {
        assertNull(MockRuleLoader.parseMockRule("# this is a comment"))
    }

    @Test
    fun testParseMockRuleNoEquals() {
        assertNull(MockRuleLoader.parseMockRule("*.email|string"))
    }

    @Test
    fun testParseMockRuleEmptyValue() {
        assertNull(MockRuleLoader.parseMockRule("*.email|string="))
    }

    @Test
    fun testParseMockRuleEmptyPattern() {
        assertNull(MockRuleLoader.parseMockRule("=@email"))
    }

    @Test
    fun testParseMockRuleComplexMockValue() {
        val result = MockRuleLoader.parseMockRule("*.age|integer=@integer(0, 120)")
        assertNotNull(result)
        assertEquals("*.age|integer", result!!.first)
        assertEquals("@integer(0, 120)", result.second)
    }

    @Test
    fun testParseMockRulePasswordMask() {
        val result = MockRuleLoader.parseMockRule("*.password|string=******")
        assertNotNull(result)
        assertEquals("*.password|string", result!!.first)
        assertEquals("******", result.second)
    }
}
