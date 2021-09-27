package com.itangcent.idea.plugin.api.export.generic

import com.itangcent.intellij.config.rule.*

object GenericClassExportRuleKeys {

    val CLASS_HAS_API: RuleKey<Boolean> = SimpleRuleKey(
            "generic.class.has.api", 
            BooleanRuleMode.ANY
    )

    val HTTP_PATH: RuleKey<String> = SimpleRuleKey(
            "generic.path", 
            StringRuleMode.SINGLE
    )

    val HTTP_METHOD: RuleKey<String> = SimpleRuleKey(
            "generic.http.method", 
            StringRuleMode.SINGLE
    )

    val METHOD_HAS_API: RuleKey<Boolean> = SimpleRuleKey(
            "generic.method.has.api", 
            BooleanRuleMode.ANY
    )

    val PARAM_AS_JSON_BODY: RuleKey<Boolean> = SimpleRuleKey(
            "generic.param.as.json.body", 
            BooleanRuleMode.ANY
    )

    val PARAM_AS_FORM_BODY: RuleKey<Boolean> = SimpleRuleKey(
            "generic.param.as.form.body", 
            BooleanRuleMode.ANY
    )

    val PARAM_AS_PATH_VAR: RuleKey<Boolean> = SimpleRuleKey(
            "generic.param.as.path.var", 
            BooleanRuleMode.ANY
    )

    val PARAM_PATH_VAR: RuleKey<String> = SimpleRuleKey(
            "generic.param.path.var", 
            StringRuleMode.SINGLE
    )

    val PARAM_AS_COOKIE: RuleKey<Boolean> = SimpleRuleKey(
            "generic.param.as.cookie", 
            BooleanRuleMode.ANY
    )

    val PARAM_COOKIE: RuleKey<String> = SimpleRuleKey(
            "generic.param.cookie", 
            StringRuleMode.SINGLE
    )

    val PARAM_COOKIE_VALUE: RuleKey<String> = SimpleRuleKey(
            "generic.param.cookie.value", 
            StringRuleMode.SINGLE
    )

    val PARAM_HEADER: RuleKey<String> = SimpleRuleKey(
            "generic.param.header", 
            StringRuleMode.SINGLE
    )

    val PARAM_NAME: RuleKey<String> = SimpleRuleKey(
            "generic.param.name", 
            StringRuleMode.SINGLE
    )

}
