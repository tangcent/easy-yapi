package com.itangcent.idea.plugin.api.export.yapi

import com.itangcent.idea.plugin.rule.MyStringRuleMode
import com.itangcent.intellij.config.rule.*

object YapiClassExportRuleKeys {

    val BEFORE_EXPORT: RuleKey<String> = SimpleRuleKey(
        "yapi.export.before",
        EventRuleMode.THROW_IN_ERROR
    )

    val TAG: RuleKey<String> = SimpleRuleKey(
        "api.tag",
        StringRuleMode.MERGE_DISTINCT
    )

    val STATUS: RuleKey<String> = SimpleRuleKey(
        "api.status", StringRuleMode.SINGLE
    )

    val FIELD_DEMO: RuleKey<String> = SimpleRuleKey(
        "field.demo",
        StringRuleMode.SINGLE
    )


    val FIELD_ADVANCED: RuleKey<List<String>> = SimpleRuleKey(
        "field.advanced",
        MyStringRuleMode.LIST
    )

    val PARAM_DEMO: RuleKey<String> = SimpleRuleKey(
        "param.demo",
        StringRuleMode.SINGLE
    )

    val OPEN: RuleKey<Boolean> = SimpleRuleKey(
        "api.open",
        BooleanRuleMode.ANY
    )
}