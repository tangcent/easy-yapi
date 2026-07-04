package com.itangcent.easyapi.rule

/**
 * Hoppscotch-specific rule keys.
 *
 * @see RuleKeys for general (shared) rule keys
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
