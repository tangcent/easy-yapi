package com.itangcent.easyapi.exporter.channel.hoppscotch

import com.itangcent.easyapi.rule.EventRuleMode
import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.rule.StringRuleMode

/**
 * Hoppscotch-specific rule keys.
 *
 * These rule keys allow users to customize Hoppscotch export behavior via
 * the rule engine (e.g., in `.easy.api.yml` files):
 *
 * | Rule Key | Purpose |
 * |----------|---------|
 * | `hopp.prerequest` | Pre-request script for a method |
 * | `hopp.class.prerequest` | Pre-request script for all methods in a class |
 * | `hopp.collection.prerequest` | Pre-request script for the entire collection |
 * | `hopp.test` | Test script for a method |
 * | `hopp.class.test` | Test script for all methods in a class |
 * | `hopp.collection.test` | Test script for the entire collection |
 * | `hopp.host` | Base URL override for endpoints |
 * | `hopp.format.after` | Post-format hook |
 *
 * @see com.itangcent.easyapi.rule.RuleKeys for general (shared) rule keys
 */
object HoppscotchRuleKeys {
    val HOPP_PREREQUEST = RuleKey.string("hopp.prerequest", StringRuleMode.MERGE)
    val HOPP_CLASS_PREREQUEST =
        RuleKey.string("hopp.class.prerequest", StringRuleMode.MERGE, aliases = listOf("class.hopp.prerequest"))
    val HOPP_COLLECTION_PREREQUEST =
        RuleKey.event("hopp.collection.prerequest", aliases = listOf("collection.hopp.prerequest"))
    val HOPP_TEST = RuleKey.string("hopp.test", StringRuleMode.MERGE)
    val HOPP_CLASS_TEST =
        RuleKey.string("hopp.class.test", StringRuleMode.MERGE, aliases = listOf("class.hopp.test"))
    val HOPP_COLLECTION_TEST = RuleKey.event("hopp.collection.test", aliases = listOf("collection.hopp.test"))
    val HOPP_HOST = RuleKey.string("hopp.host")
    val HOPP_FORMAT_AFTER = RuleKey.event("hopp.format.after", EventRuleMode.THROW_IN_ERROR)
}
