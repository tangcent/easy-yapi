package com.itangcent.easyapi.rule

import org.junit.Assert.*
import org.junit.Test

class RuleKeyTest {

    @Test
    fun testStringKey_name() {
        val key = RuleKey.string("api.name")
        assertEquals("api.name", key.name)
        assertEquals("api.name", key.toString())
    }

    @Test
    fun testStringKey_defaultMode() {
        val key = RuleKey.string("api.name")
        assertEquals(StringRuleMode.SINGLE, key.stringMode)
    }

    @Test
    fun testStringKey_customMode() {
        val key = RuleKey.string("api.tags", StringRuleMode.MERGE)
        assertEquals(StringRuleMode.MERGE, key.stringMode)
    }

    @Test
    fun testStringKey_aliases() {
        val key = RuleKey.string("api.name", aliases = listOf("name", "title"))
        assertEquals(listOf("api.name", "name", "title"), key.allNames)
    }

    @Test
    fun testStringKey_noAliases() {
        val key = RuleKey.string("api.name")
        assertEquals(listOf("api.name"), key.allNames)
    }

    @Test
    fun testBooleanKey_name() {
        val key = RuleKey.boolean("field.required")
        assertEquals("field.required", key.name)
    }

    @Test
    fun testBooleanKey_defaultMode() {
        val key = RuleKey.boolean("field.required")
        assertEquals(BooleanRuleMode.ANY, key.booleanMode)
    }

    @Test
    fun testBooleanKey_customMode() {
        val key = RuleKey.boolean("field.required", BooleanRuleMode.ALL)
        assertEquals(BooleanRuleMode.ALL, key.booleanMode)
    }

    @Test
    fun testBooleanKey_aliases() {
        val key = RuleKey.boolean("field.required", aliases = listOf("required"))
        assertEquals(listOf("field.required", "required"), key.allNames)
    }

    @Test
    fun testIntKey_name() {
        val key = RuleKey.int("field.order")
        assertEquals("field.order", key.name)
    }

    @Test
    fun testIntKey_aliases() {
        val key = RuleKey.int("field.order", listOf("order"))
        assertEquals(listOf("field.order", "order"), key.allNames)
    }

    @Test
    fun testEventKey_name() {
        val key = RuleKey.event("api.complete")
        assertEquals("api.complete", key.name)
    }

    @Test
    fun testEventKey_defaultMode() {
        val key = RuleKey.event("api.complete")
        assertEquals(EventRuleMode.IGNORE_ERROR, key.eventMode)
    }

    @Test
    fun testEventKey_customMode() {
        val key = RuleKey.event("api.complete", EventRuleMode.THROW_IN_ERROR)
        assertEquals(EventRuleMode.THROW_IN_ERROR, key.eventMode)
    }

    @Test
    fun testAllNames_withMultipleAliases() {
        val key = RuleKey.string("primary", aliases = listOf("alias1", "alias2", "alias3"))
        assertEquals(listOf("primary", "alias1", "alias2", "alias3"), key.allNames)
    }
}
