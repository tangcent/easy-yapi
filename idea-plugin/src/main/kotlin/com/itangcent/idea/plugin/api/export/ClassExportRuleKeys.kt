package com.itangcent.idea.plugin.api.export

import com.itangcent.intellij.config.rule.*

object ClassExportRuleKeys {

    val MODULE: RuleKey<String> = SimpleRuleKey("module", StringRule::class,
            StringRuleMode.SINGLE)

    val IGNORE: RuleKey<Boolean> = SimpleRuleKey("ignore", BooleanRule::class,
            BooleanRuleMode.ANY)

    val METHOD_DOC: RuleKey<String> = SimpleRuleKey("doc.method", StringRule::class,
            StringRuleMode.MERGE_DISTINCT)

    val PARAM_REQUIRED: RuleKey<Boolean> = SimpleRuleKey("param.required", BooleanRule::class,
            BooleanRuleMode.ANY)

    val FIELD_REQUIRED: RuleKey<Boolean> = SimpleRuleKey("field.required", BooleanRule::class,
            BooleanRuleMode.ANY)

}