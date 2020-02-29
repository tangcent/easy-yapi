package com.itangcent.idea.plugin.api.export

import com.itangcent.intellij.config.rule.*

object ClassExportRuleKeys {

    val MODULE: RuleKey<String> = SimpleRuleKey(
            "module", StringRule::class,
            StringRuleMode.SINGLE
    )

    val IGNORE: RuleKey<Boolean> = SimpleRuleKey(
            "ignore", BooleanRule::class,
            BooleanRuleMode.ANY
    )

    //filter class for methodDoc(rpc)
    val CLASS_FILTER: RuleKey<Boolean> = SimpleRuleKey(
            "mdoc.class.filter", BooleanRule::class,
            BooleanRuleMode.ANY
    )

    //filter method for methodDoc(rpc)
    val METHOD_FILTER: RuleKey<Boolean> = SimpleRuleKey(
            "mdoc.method.filter", BooleanRule::class,
            BooleanRuleMode.ANY
    )

    val METHOD_DOC_PATH: RuleKey<String> = SimpleRuleKey("mdoc.method.path", StringRule::class,
            StringRuleMode.SINGLE)

    val METHOD_DOC_METHOD: RuleKey<String> = SimpleRuleKey("mdoc.method.http.method", StringRule::class,
            StringRuleMode.SINGLE)

    val PARAM_DOC: RuleKey<String> = SimpleRuleKey("doc.param", StringRule::class,
            StringRuleMode.MERGE_DISTINCT)

    val METHOD_DOC: RuleKey<String> = SimpleRuleKey(
            "doc.method", StringRule::class,
            StringRuleMode.MERGE_DISTINCT
    )

    val CLASS_DOC: RuleKey<String> = SimpleRuleKey(
            "doc.class", StringRule::class,
            StringRuleMode.MERGE_DISTINCT
    )

    val METHOD_ADDITIONAL_HEADER: RuleKey<String> = SimpleRuleKey(
            "method.additional.header", StringRule::class,
            StringRuleMode.MERGE_DISTINCT
    )

    val METHOD_ADDITIONAL_PARAM: RuleKey<String> = SimpleRuleKey(
            "method.additional.param", StringRule::class,
            StringRuleMode.MERGE_DISTINCT
    )

    val METHOD_ADDITIONAL_RESPONSE_HEADER: RuleKey<String> = SimpleRuleKey(
            "method.additional.response.header", StringRule::class,
            StringRuleMode.MERGE_DISTINCT
    )

    val PARAM_REQUIRED: RuleKey<Boolean> = SimpleRuleKey(
            "param.required", BooleanRule::class,
            BooleanRuleMode.ANY
    )

    val PARAM_IGNORE: RuleKey<Boolean> = SimpleRuleKey(
            "param.ignore", BooleanRule::class,
            BooleanRuleMode.ANY
    )

    val PARAM_DEFAULT_VALUE: RuleKey<String> = SimpleRuleKey(
            "param.default.value", StringRule::class,
            StringRuleMode.MERGE_DISTINCT
    )

    val FIELD_REQUIRED: RuleKey<Boolean> = SimpleRuleKey(
            "field.required", BooleanRule::class,
            BooleanRuleMode.ANY
    )

    val CLASS_PREFIX_PATH: RuleKey<String> = SimpleRuleKey(
            "class.prefix.path", StringRule::class,
            StringRuleMode.SINGLE
    )

    /**
     * the main goal of the {@return}
     */
    val METHOD_RETURN_MAIN: RuleKey<String> = SimpleRuleKey(
            "method.return.main", StringRule::class,
            StringRuleMode.SINGLE
    )

    /**
     * the real return type of method
     */
    val METHOD_RETURN: RuleKey<String> = SimpleRuleKey(
            "method.return", StringRule::class,
            StringRuleMode.SINGLE
    )

    /**
     * name of api
     */
    val API_NAME: RuleKey<String> = SimpleRuleKey(
            "api.name", StringRule::class,
            StringRuleMode.SINGLE
    )

    //default http method of api(method)
    val METHOD_DEFAULT_HTTP_METHOD: RuleKey<String> = SimpleRuleKey(
            "method.default.http.method", StringRule::class,
            StringRuleMode.SINGLE
    )

    val FIELD_MOCK: RuleKey<String> = SimpleRuleKey(
            "field.mock", StringRule::class,
            StringRuleMode.SINGLE
    )

    val FIELD_DEFAULT_VALUE: RuleKey<String> = SimpleRuleKey(
            "field.default.value", StringRule::class,
            StringRuleMode.SINGLE
    )

    val POST_MAN_HOST: RuleKey<String> = SimpleRuleKey(
            "postman.host", StringRule::class,
            StringRuleMode.SINGLE
    )
}
