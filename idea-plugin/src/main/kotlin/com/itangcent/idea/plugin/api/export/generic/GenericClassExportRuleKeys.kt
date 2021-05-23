package com.itangcent.idea.plugin.api.export.generic

import com.itangcent.intellij.config.rule.*

object GenericClassExportRuleKeys {

    val CLASS_HAS_API: RuleKey<Boolean> = SimpleRuleKey(
            "generic.class.has.api", BooleanRule::class,
            BooleanRuleMode.ANY
    )

    val HTTP_PATH: RuleKey<String> = SimpleRuleKey(
            "generic.path", StringRule::class,
            StringRuleMode.SINGLE
    )

    val HTTP_METHOD: RuleKey<String> = SimpleRuleKey(
            "generic.http.method", StringRule::class,
            StringRuleMode.SINGLE
    )

    val METHOD_HAS_API: RuleKey<Boolean> = SimpleRuleKey(
            "generic.method.has.api", BooleanRule::class,
            BooleanRuleMode.ANY
    )

    val PARAM_AS_JSON_BODY: RuleKey<Boolean> = SimpleRuleKey(
            "generic.param.as.json.body", BooleanRule::class,
            BooleanRuleMode.ANY
    )

    val PARAM_AS_FORM_BODY: RuleKey<Boolean> = SimpleRuleKey(
            "generic.param.as.form.body", BooleanRule::class,
            BooleanRuleMode.ANY
    )

    val PARAM_AS_PATH_VAR: RuleKey<Boolean> = SimpleRuleKey(
            "generic.param.as.path.var", BooleanRule::class,
            BooleanRuleMode.ANY
    )

    val PARAM_PATH_VAR: RuleKey<String> = SimpleRuleKey(
            "generic.param.path.var", StringRule::class,
            StringRuleMode.SINGLE
    )

    val PARAM_AS_COOKIE: RuleKey<Boolean> = SimpleRuleKey(
            "generic.param.as.cookie", BooleanRule::class,
            BooleanRuleMode.ANY
    )

    val PARAM_COOKIE: RuleKey<String> = SimpleRuleKey(
            "generic.param.cookie", StringRule::class,
            StringRuleMode.SINGLE
    )

    val PARAM_COOKIE_VALUE: RuleKey<String> = SimpleRuleKey(
            "generic.param.cookie.value", StringRule::class,
            StringRuleMode.SINGLE
    )

    val PARAM_HEADER: RuleKey<String> = SimpleRuleKey(
            "generic.param.header", StringRule::class,
            StringRuleMode.SINGLE
    )

    val PARAM_NAME: RuleKey<String> = SimpleRuleKey(
            "generic.param.name", StringRule::class,
            StringRuleMode.SINGLE
    )

}
