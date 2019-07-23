package com.itangcent.idea.plugin.api.export.yapi

import com.itangcent.intellij.config.rule.RuleKey
import com.itangcent.intellij.config.rule.SimpleRuleKey
import com.itangcent.intellij.config.rule.StringRule
import com.itangcent.intellij.config.rule.StringRuleMode

object YapiClassExportRuleKeys {

    val TAG: RuleKey<String> = SimpleRuleKey("api.tag", StringRule::class,
            StringRuleMode.MERGE_DISTINCT)

    val STATUS: RuleKey<String> = SimpleRuleKey("api.status", StringRule::class,
            StringRuleMode.SINGLE)

}