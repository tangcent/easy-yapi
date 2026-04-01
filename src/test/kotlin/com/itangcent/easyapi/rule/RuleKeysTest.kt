package com.itangcent.easyapi.rule

import org.junit.Assert.*
import org.junit.Test

class RuleKeysTest {

    @Test
    fun testStringKeyProperties() {
        val key = RuleKeys.API_NAME
        assertEquals("api.name", key.name)
        assertTrue(key.mode is StringRuleMode.SINGLE)
        assertTrue(key.aliases.isEmpty())
        assertEquals(listOf("api.name"), key.allNames)
    }

    @Test
    fun testBooleanKeyProperties() {
        val key = RuleKeys.FIELD_REQUIRED
        assertEquals("field.required", key.name)
        assertTrue(key.mode is BooleanRuleMode.ANY)
    }

    @Test
    fun testEventKeyProperties() {
        val key = RuleKeys.JSON_FIELD_PARSE_BEFORE
        assertEquals("json.field.parse.before", key.name)
        assertTrue(key.mode is EventRuleMode)
    }

    @Test
    fun testIntKeyProperties() {
        val key = RuleKeys.FIELD_MAX_DEPTH
        assertEquals("field.max.depth", key.name)
        assertEquals(IntRuleMode, key.mode)
    }

    @Test
    fun testAliases() {
        val key = RuleKeys.PARAM_DOC
        assertEquals("param.doc", key.name)
        assertEquals(listOf("doc.param"), key.aliases)
        assertEquals(listOf("param.doc", "doc.param"), key.allNames)
    }

    @Test
    fun testMergeModeKeys() {
        assertTrue(RuleKeys.METHOD_ADDITIONAL_HEADER.mode is StringRuleMode.MERGE)
        assertTrue(RuleKeys.FIELD_ADVANCED.mode is StringRuleMode.MERGE)
        assertTrue(RuleKeys.JSON_ADDITIONAL_FIELD.mode is StringRuleMode.MERGE)
    }

    @Test
    fun testPostmanKeys() {
        assertEquals("postman.prerequest", RuleKeys.POSTMAN_PREREQUEST.name)
        assertTrue(RuleKeys.POSTMAN_PREREQUEST.mode is StringRuleMode.MERGE)
        assertEquals(listOf("class.postman.prerequest"), RuleKeys.POSTMAN_CLASS_PREREQUEST.aliases)
    }

    @Test
    fun testClassRecognizerKeys() {
        assertEquals("class.is.spring.ctrl", RuleKeys.CLASS_IS_CTRL.name)
        assertEquals(listOf("class.is.ctrl"), RuleKeys.CLASS_IS_CTRL.aliases)
    }

    @Test
    fun testFieldMockIsStringKey() {
        // This is the key fix — field.mock must be a StringKey so #mock returns tag value, not boolean
        assertTrue(RuleKeys.FIELD_MOCK is RuleKey.StringKey)
        assertEquals("field.mock", RuleKeys.FIELD_MOCK.name)
    }
}
