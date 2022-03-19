package com.itangcent.idea.plugin.api.export.markdown

import com.itangcent.intellij.config.rule.RuleKey
import com.itangcent.intellij.config.rule.SimpleRuleKey
import com.itangcent.intellij.config.rule.StringRuleMode

object MarkdownExportRuleKeys {

    val HN_TITLE: RuleKey<String> = SimpleRuleKey(
        "md.title", StringRuleMode.SINGLE
    )

    val BASIC: RuleKey<String> = SimpleRuleKey(
        "md.basic", StringRuleMode.SINGLE
    )

    val BASIC_PATH: RuleKey<String> = SimpleRuleKey(
        "md.basic.path", StringRuleMode.SINGLE
    )

    val BASIC_METHOD: RuleKey<String> = SimpleRuleKey(
        "md.basic.method", StringRuleMode.SINGLE
    )

    val BASIC_DESC: RuleKey<String> = SimpleRuleKey(
        "md.basic.desc", StringRuleMode.SINGLE
    )

    val REQUEST: RuleKey<String> = SimpleRuleKey(
        "md.request", StringRuleMode.SINGLE
    )

    val REQUEST_PATH: RuleKey<String> = SimpleRuleKey(
        "md.request.path", StringRuleMode.SINGLE
    )

    val REQUEST_HEADERS: RuleKey<String> = SimpleRuleKey(
        "md.request.headers", StringRuleMode.SINGLE
    )

    val REQUEST_QUERY: RuleKey<String> = SimpleRuleKey(
        "md.request.query", StringRuleMode.SINGLE
    )

    val REQUEST_BODY: RuleKey<String> = SimpleRuleKey(
        "md.request.body", StringRuleMode.SINGLE
    )

    val REQUEST_BODY_DEMO: RuleKey<String> = SimpleRuleKey(
        "md.request.body.demo", StringRuleMode.SINGLE
    )

    val REQUEST_FORM: RuleKey<String> = SimpleRuleKey(
        "md.request.form", StringRuleMode.SINGLE
    )

    val RESPONSE: RuleKey<String> = SimpleRuleKey(
        "md.response", StringRuleMode.SINGLE
    )

    val RESPONSE_HEADERS: RuleKey<String> = SimpleRuleKey(
        "md.response.headers", StringRuleMode.SINGLE
    )

    val RESPONSE_BODY: RuleKey<String> = SimpleRuleKey(
        "md.response.body", StringRuleMode.SINGLE
    )

    val RESPONSE_BODY_DEMO: RuleKey<String> = SimpleRuleKey(
        "md.response.body.demo", StringRuleMode.SINGLE
    )

    val METHOD_DOC_DESC: RuleKey<String> = SimpleRuleKey(
        "md.methodDoc.desc", StringRuleMode.SINGLE
    )
    val METHOD_DOC_PARAMS: RuleKey<String> = SimpleRuleKey(
        "md.methodDoc.params", StringRuleMode.SINGLE
    )
    val METHOD_DOC_RETURN: RuleKey<String> = SimpleRuleKey(
        "md.methodDoc.return", StringRuleMode.SINGLE
    )

}