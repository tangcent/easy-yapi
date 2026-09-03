package com.itangcent.easyapi.channel.yapi

import com.itangcent.easyapi.core.rule.ContextKind
import com.itangcent.easyapi.core.rule.EventRuleMode
import com.itangcent.easyapi.core.rule.OutputShape
import com.itangcent.easyapi.core.rule.RuleKey
import com.itangcent.easyapi.core.rule.RuleKeyScheme

/**
 * YApi-specific rule keys.
 * Extracted from [RuleKeys] so the shared file can be identical between
 * easy-api and easy-yapi.
 *
 * @see com.itangcent.easyapi.core.rule.RuleKeys for general (shared) rule keys
 */
object YapiRuleKeys {
    val YAPI_PROJECT = RuleKey.string(
        "yapi.project", aliases = listOf("project", "module"),
        scheme = RuleKeyScheme(
            summary = "YApi project (token/id) the endpoint is exported under.",
            contextKinds = listOf(ContextKind.METHOD, ContextKind.CLASS),
            outputShape = OutputShape.STRING
        )
    )
    val YAPI_EXPORT_BEFORE = RuleKey.event(
        "yapi.export.before", EventRuleMode.THROW_IN_ERROR,
        scheme = RuleKeyScheme(
            summary = "Fires once before the YApi export starts.",
            contextKinds = listOf(ContextKind.EMPTY),
            outputShape = OutputShape.EVENT
        )
    )
    val YAPI_SAVE_BEFORE = RuleKey.event(
        "yapi.save.before", EventRuleMode.THROW_IN_ERROR,
        scheme = RuleKeyScheme(
            summary = "Fires before an endpoint is uploaded to YApi; the document can be mutated.",
            contextKinds = listOf(ContextKind.METHOD, ContextKind.CLASS),
            outputShape = OutputShape.EVENT
        )
    )
    val YAPI_SAVE_AFTER = RuleKey.event(
        "yapi.save.after", EventRuleMode.THROW_IN_ERROR,
        scheme = RuleKeyScheme(
            summary = "Fires after an endpoint is uploaded to YApi; the upload result is exposed.",
            contextKinds = listOf(ContextKind.METHOD, ContextKind.CLASS),
            outputShape = OutputShape.EVENT
        )
    )
}
