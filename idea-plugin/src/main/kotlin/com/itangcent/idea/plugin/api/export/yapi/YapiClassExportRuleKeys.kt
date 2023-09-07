package com.itangcent.idea.plugin.api.export.yapi

import com.itangcent.idea.plugin.rule.MyStringRuleMode
import com.itangcent.intellij.config.rule.*

object YapiClassExportRuleKeys {

    val BEFORE_EXPORT: RuleKey<String> = SimpleRuleKey(
        "yapi.export.before",
        EventRuleMode.THROW_IN_ERROR
    )

    val BEFORE_SAVE: RuleKey<String> = SimpleRuleKey(
        "yapi.save.before",
        EventRuleMode.THROW_IN_ERROR
    )

    val AFTER_SAVE: RuleKey<String> = SimpleRuleKey(
        "yapi.save.after",
        EventRuleMode.THROW_IN_ERROR
    )

    val TAG: RuleKey<String> = SimpleRuleKey(
        "api.tag",
        StringRuleMode.MERGE_DISTINCT
    )

    val STATUS: RuleKey<String> = SimpleRuleKey(
        "api.status", StringRuleMode.SINGLE
    )

    val FIELD_MOCK: RuleKey<String> = SimpleRuleKey(
        "field.mock",
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

    val AFTER_FORMAT: RuleKey<String> = SimpleRuleKey(
        "yapi.format.after",
        EventRuleMode.THROW_IN_ERROR
    )
}