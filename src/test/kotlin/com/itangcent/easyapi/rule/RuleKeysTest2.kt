package com.itangcent.easyapi.rule

import org.junit.Assert.*
import org.junit.Test

class RuleKeysTest2 {

    @Test
    fun testApiNameKey() {
        assertEquals("api.name", RuleKeys.API_NAME.name)
        assertTrue(RuleKeys.API_NAME is RuleKey.StringKey)
    }

    @Test
    fun testApiTagKey() {
        assertEquals("api.tag", RuleKeys.API_TAG.name)
        assertTrue(RuleKeys.API_TAG is RuleKey.StringKey)
    }

    @Test
    fun testIgnoreKey() {
        assertEquals("ignore", RuleKeys.IGNORE.name)
        assertTrue(RuleKeys.IGNORE is RuleKey.BooleanKey)
    }

    @Test
    fun testMethodDocKey_mergeDistinct() {
        assertEquals("method.doc", RuleKeys.METHOD_DOC.name)
        assertEquals(StringRuleMode.MERGE_DISTINCT, (RuleKeys.METHOD_DOC as RuleKey.StringKey).stringMode)
    }

    @Test
    fun testFieldRequiredKey() {
        assertEquals("field.required", RuleKeys.FIELD_REQUIRED.name)
        assertTrue(RuleKeys.FIELD_REQUIRED is RuleKey.BooleanKey)
    }

    @Test
    fun testFieldIgnoreKey() {
        assertEquals("field.ignore", RuleKeys.FIELD_IGNORE.name)
        assertTrue(RuleKeys.FIELD_IGNORE is RuleKey.BooleanKey)
    }

    @Test
    fun testFieldDocKey_merge() {
        assertEquals("field.doc", RuleKeys.FIELD_DOC.name)
        assertEquals(StringRuleMode.MERGE_DISTINCT, (RuleKeys.FIELD_DOC as RuleKey.StringKey).stringMode)
        assertTrue(RuleKeys.FIELD_DOC.aliases.contains("doc.field"))
    }

    @Test
    fun testParamDocKey_aliases() {
        assertEquals("param.doc", RuleKeys.PARAM_DOC.name)
        assertTrue(RuleKeys.PARAM_DOC.aliases.contains("doc.param"))
    }

    @Test
    fun testEventKeys() {
        assertTrue(RuleKeys.JSON_FIELD_PARSE_BEFORE is RuleKey.EventKey)
        assertTrue(RuleKeys.JSON_CLASS_PARSE_AFTER is RuleKey.EventKey)
        assertTrue(RuleKeys.EXPORT_AFTER is RuleKey.EventKey)
    }

    @Test
    fun testIntKeys() {
        assertTrue(RuleKeys.FIELD_MAX_DEPTH is RuleKey.IntKey)
        assertTrue(RuleKeys.PARAM_MAX_DEPTH is RuleKey.IntKey)
    }

    @Test
    fun testPostmanFormatAfter_throwInError() {
        val key = RuleKeys.POSTMAN_FORMAT_AFTER as RuleKey.EventKey
        assertEquals(EventRuleMode.THROW_IN_ERROR, key.eventMode)
    }

    @Test
    fun testClassIsCtrl_aliases() {
        assertEquals("class.is.spring.ctrl", RuleKeys.CLASS_IS_CTRL.name)
        assertTrue(RuleKeys.CLASS_IS_CTRL.aliases.contains("class.is.ctrl"))
    }

    @Test
    fun testHttpCallEvents() {
        assertTrue(RuleKeys.HTTP_CALL_BEFORE is RuleKey.EventKey)
        assertTrue(RuleKeys.HTTP_CALL_AFTER is RuleKey.EventKey)
    }

    @Test
    fun testMethodReturnMain() {
        assertEquals("method.return.main", RuleKeys.METHOD_RETURN_MAIN.name)
        assertTrue(RuleKeys.METHOD_RETURN_MAIN is RuleKey.StringKey)
    }

    @Test
    fun testPostmanPrerequest_merge() {
        assertEquals(StringRuleMode.MERGE, (RuleKeys.POSTMAN_PREREQUEST as RuleKey.StringKey).stringMode)
    }
}
