package com.itangcent.easyapi.channel.yapi

import com.itangcent.easyapi.core.rule.ContextKind
import com.itangcent.easyapi.core.rule.OutputShape
import com.itangcent.easyapi.core.rule.RuleKey
import com.itangcent.easyapi.core.rule.RuleKeyScheme
import com.itangcent.easyapi.core.rule.RuleKeys
import com.itangcent.easyapi.core.rule.StringRuleMode

/**
 * YApi-specific rule keys for API metadata (tags, status, open).
 *
 * These were previously in the shared [RuleKeys] but are YApi-specific (no
 * easy-api channel consumes them). They live here so the shared [RuleKeys]
 * stays minimal and free of any single channel's concerns.
 *
 * Note: `field.mock` is *not* re-declared here — the shared
 * [RuleKeys.FIELD_MOCK] already covers it for every channel, so re-declaring
 * it here would only shadow (and be de-duplicated against) the general key.
 *
 * @see YapiMetadataResolver
 */
object YapiMetaRuleKeys {
    /** Rule key for resolving API tags (e.g. from `api.tag` rules; may be comma/newline-separated). */
    val API_TAG = RuleKey.string(
        "api.tag", StringRuleMode.MERGE_DISTINCT,
        scheme = RuleKeyScheme(
            summary = "Tag(s) attached to the API (e.g. for YApi grouping/filtering).",
            contextKinds = listOf(ContextKind.METHOD),
            outputShape = OutputShape.MERGED_STRING
        )
    )

    /** Rule key for resolving the API lifecycle status (e.g. "undone", "deprecated"). */
    val API_STATUS = RuleKey.string(
        "api.status",
        scheme = RuleKeyScheme(
            summary = "Lifecycle status of the API (e.g. \"undone\", \"deprecated\").",
            contextKinds = listOf(ContextKind.METHOD),
            outputShape = OutputShape.STRING
        )
    )

    /** Rule key for resolving whether an API is open/exposed. */
    val API_OPEN = RuleKey.boolean(
        "api.open",
        scheme = RuleKeyScheme(
            summary = "Whether the API is open/exposed (default false).",
            contextKinds = listOf(ContextKind.METHOD),
            outputShape = OutputShape.BOOLEAN
        )
    )
}
