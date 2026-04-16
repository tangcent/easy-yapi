package com.itangcent.easyapi.rule

import org.junit.Assert.*
import org.junit.Test

class ConfigRuleTest {

    @Test
    fun testConfigRuleWithNoFilter() {
        val rule = ConfigRule(expression = "groovy:it.name()")
        
        assertEquals("groovy:it.name()", rule.expression)
        assertNull("Filter should be null", rule.filter)
    }

    @Test
    fun testConfigRuleWithFilter() {
        val rule = ConfigRule(
            expression = "groovy:it.name().toUpperCase()",
            filter = "true"
        )
        
        assertEquals("groovy:it.name().toUpperCase()", rule.expression)
        assertEquals("true", rule.filter)
    }

    @Test
    fun testConfigRuleEquality() {
        val rule1 = ConfigRule(expression = "test", filter = "true")
        val rule2 = ConfigRule(expression = "test", filter = "true")
        val rule3 = ConfigRule(expression = "test", filter = null)
        
        assertEquals("Same rules should be equal", rule1, rule2)
        assertNotEquals("Different filters should not be equal", rule1, rule3)
    }

    @Test
    fun testConfigRuleCopy() {
        val original = ConfigRule(expression = "original", filter = "filter")
        val copy = original.copy(expression = "copied")
        
        assertEquals("copied", copy.expression)
        assertEquals("filter", copy.filter)
    }

    @Test
    fun testConfigRuleToString() {
        val rule = ConfigRule(expression = "test", filter = "true")
        val str = rule.toString()
        
        assertTrue("toString should contain expression", str.contains("test"))
        assertTrue("toString should contain filter", str.contains("true"))
    }
}
