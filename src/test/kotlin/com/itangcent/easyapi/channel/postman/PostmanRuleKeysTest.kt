package com.itangcent.easyapi.channel.postman

import com.itangcent.easyapi.core.rule.EventRuleMode
import com.itangcent.easyapi.core.rule.OutputShape
import com.itangcent.easyapi.core.rule.RuleKey
import com.itangcent.easyapi.core.rule.StringRuleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostmanRuleKeysTest {

    @Test
    fun `script keys are merge-mode strings producing postman js`() {
        assertTrue(PostmanRuleKeys.POSTMAN_PREREQUEST.mode is StringRuleMode.MERGE)
        assertTrue(PostmanRuleKeys.POSTMAN_TEST.mode is StringRuleMode.MERGE)
        assertTrue(PostmanRuleKeys.POSTMAN_CLASS_PREREQUEST.mode is StringRuleMode.MERGE)
        assertTrue(PostmanRuleKeys.POSTMAN_CLASS_TEST.mode is StringRuleMode.MERGE)

        // "Output format" is what the scheme declares — the value shape is a
        // merged string of Postman JS. There is deliberately no per-key
        // execution mode: the value may be literal or a groovy: rule.
        assertEquals(OutputShape.MERGED_STRING, PostmanRuleKeys.POSTMAN_PREREQUEST.scheme.outputShape)
        assertEquals(OutputShape.MERGED_STRING, PostmanRuleKeys.POSTMAN_TEST.scheme.outputShape)
    }

    @Test
    fun `aliases match legacy names`() {
        assertEquals(listOf("class.postman.prerequest"), PostmanRuleKeys.POSTMAN_CLASS_PREREQUEST.aliases)
        assertEquals(listOf("class.postman.test"), PostmanRuleKeys.POSTMAN_CLASS_TEST.aliases)
        assertEquals(listOf("collection.postman.prerequest"), PostmanRuleKeys.POSTMAN_COLLECTION_PREREQUEST.aliases)
        assertEquals(listOf("collection.postman.test"), PostmanRuleKeys.POSTMAN_COLLECTION_TEST.aliases)
    }

    @Test
    fun `event keys use event modes and expose bindings`() {
        assertTrue(PostmanRuleKeys.POSTMAN_COLLECTION_PREREQUEST is RuleKey.EventKey)
        assertTrue(PostmanRuleKeys.POSTMAN_COLLECTION_TEST is RuleKey.EventKey)
        val formatAfter = PostmanRuleKeys.POSTMAN_FORMAT_AFTER as RuleKey.EventKey
        assertEquals("postman.format.after", formatAfter.name)
        assertEquals(EventRuleMode.THROW_IN_ERROR, formatAfter.eventMode)
    }

    @Test
    fun `script key scheme self-describes literal and groovy modes`() {
        // The contract: a Postman script value is injected literally by
        // default; only a `groovy:`-prefixed value is evaluated against the PSI
        // element. The scheme notes must carry that so list_rule_keys surfaces
        // it to the AI.
        val notes = PostmanRuleKeys.POSTMAN_PREREQUEST.scheme.notes
        assertTrue("notes should mention literal injection: $notes", notes.any { it.contains("injected literally") })
        assertTrue("notes should mention the groovy: prefix: $notes", notes.any { it.contains("groovy:") })
        assertTrue(
            "notes should state bindings are only for groovy: $notes",
            notes.any { it.contains("it/helper/session") }
        )
    }

    @Test
    fun `channel registers the eight keys via reflection`() {
        val channelKeys = PostmanChannel().ruleKeys().map { it.name }.toSet()
        assertEquals(
            setOf(
                "postman.prerequest",
                "postman.class.prerequest",
                "postman.collection.prerequest",
                "postman.test",
                "postman.class.test",
                "postman.collection.test",
                "postman.host",
                "postman.format.after"
            ),
            channelKeys
        )
    }

    @Test
    fun `reflection collection finds all keys`() {
        val keys = RuleKey.collectFrom(PostmanRuleKeys).map { it.name }.toSet()
        assertTrue(keys.contains("postman.prerequest"))
        assertTrue(keys.contains("postman.test"))
        assertTrue(keys.contains("postman.format.after"))
    }
}