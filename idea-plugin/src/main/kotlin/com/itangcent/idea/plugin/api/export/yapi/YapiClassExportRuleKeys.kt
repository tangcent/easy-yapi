package com.itangcent.idea.plugin.api.export.yapi

import com.itangcent.intellij.config.rule.*

object YapiClassExportRuleKeys {

    val TAG: RuleKey<String> = SimpleRuleKey(
            "api.tag", StringRule::class,
            StringRuleMode.MERGE_DISTINCT)

    val STATUS: RuleKey<String> = SimpleRuleKey(
            "api.status", StringRule::class,
            StringRuleMode.SINGLE)

    val FIELD_DEMO: RuleKey<String> = SimpleRuleKey(
            "field.demo", StringRule::class,
            StringRuleMode.SINGLE
    )

    val PARAM_DEMO: RuleKey<String> = SimpleRuleKey(
            "param.demo", StringRule::class,
            StringRuleMode.SINGLE
    )

    val OPEN: RuleKey<Boolean> = SimpleRuleKey(
            "api.open", BooleanRule::class,
            BooleanRuleMode.ANY)
}