package com.itangcent.easyapi.rule

/**
 * V1 extensibility stub — per-channel rule keys for the dummy channel.
 *
 * Proves the per-channel rule-key slot (C5 in the Channel Author Contract) is
 * pluggable: a channel contributes its own `object` of rule keys without
 * touching the shared [RuleKeys].
 */
object DummyRuleKeys {
    val DUMMY_RULE = RuleKey.string("dummy.rule")
}
