package com.itangcent.easyapi.core.rule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [RuleKey.collectFrom] — reflection helper that enumerates every
 * `RuleKey<*>` property on an `object`.
 */
class RuleKeyTest {

    @Test
    fun collectFromReturnsAllRuleKeyPropertiesFromRuleKeys() {
        val keys = RuleKey.collectFrom(RuleKeys)
        // Spot-check a few representative keys — at minimum API_NAME and
        // FIELD_REQUIRED must be present.
        val names = keys.map { it.name }
        assertTrue("API_NAME missing: $names", RuleKeys.API_NAME.name in names)
        assertTrue("FIELD_REQUIRED missing: $names", RuleKeys.FIELD_REQUIRED.name in names)
        assertTrue("METHOD_ADDITIONAL_HEADER missing: $names", RuleKeys.METHOD_ADDITIONAL_HEADER.name in names)
    }

    @Test
    fun collectFromSkipsNonRuleKeyProperties() {
        // A synthetic object with a mix of RuleKey and non-RuleKey properties.
        val holder = object {
            val aKey: RuleKey<String> = RuleKey.string("test.alpha")
            val bKey: RuleKey<Boolean> = RuleKey.boolean("test.beta")
            val notAKey: String = "ignored"
            val alsoNotAKey: Int = 42
        }
        val keys = RuleKey.collectFrom(holder)
        assertEquals(2, keys.size)
        val names = keys.map { it.name }.toSet()
        assertEquals(setOf("test.alpha", "test.beta"), names)
    }

    @Test
    fun collectFromReturnsEmptyForPlainObject() {
        val keys = RuleKey.collectFrom("just a string")
        assertTrue(keys.isEmpty())
    }

    @Test
    fun collectFromSkipsPropertiesWithThrowingGetters() {
        val holder = object {
            val goodKey: RuleKey<String> = RuleKey.string("good.one")
            // A property whose getter throws — collectFrom must skip it
            // rather than propagating the exception.
            val badKey: RuleKey<String> get() = error("boom")
        }
        val keys = RuleKey.collectFrom(holder)
        assertEquals(1, keys.size)
        assertEquals("good.one", keys[0].name)
    }

    @Test
    fun collectFromFromChannelRuleKeysObject() {
        // The real channel RuleKeys objects are the primary use case.
        // easy-yapi has YapiRuleKeys + YapiMetaRuleKeys; verify both return keys.
        val yapiKeys = RuleKey.collectFrom(
            com.itangcent.easyapi.channel.yapi.YapiRuleKeys
        )
        assertTrue("YapiRuleKeys returned no keys", yapiKeys.isNotEmpty())
        val metaKeys = RuleKey.collectFrom(
            com.itangcent.easyapi.channel.yapi.YapiMetaRuleKeys
        )
        assertTrue("YapiMetaRuleKeys returned no keys", metaKeys.isNotEmpty())

        // easy-api has PostmanRuleKeys; verify it returns at least one key.
        val keys = RuleKey.collectFrom(
            com.itangcent.easyapi.channel.postman.PostmanRuleKeys
        )
        assertTrue("PostmanRuleKeys returned no keys", keys.isNotEmpty())
        val names = keys.map { it.name }
        assertTrue(
            "POST_PRE_REQUEST missing: $names",
            com.itangcent.easyapi.channel.postman.PostmanRuleKeys.POST_PRE_REQUEST.name in names
        )
    }
}
