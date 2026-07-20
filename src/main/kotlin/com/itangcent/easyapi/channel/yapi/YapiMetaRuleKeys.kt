package com.itangcent.easyapi.channel.yapi

import com.itangcent.easyapi.core.rule.RuleKey
import com.itangcent.easyapi.core.rule.RuleKeys
import com.itangcent.easyapi.core.rule.StringRuleMode

/**
 * YApi-specific rule keys for API metadata (tags, status, open, field mock).
 *
 * These were previously in the shared [RuleKeys] but are YApi-specific (no
 * easy-api channel consumes them). They live here so the shared [RuleKeys]
 * stays minimal and free of any single channel's concerns.
 *
 * @see YapiMetadataResolver
 */
object YapiMetaRuleKeys {
    /** Rule key for resolving API tags (e.g. from `api.tag` rules). */
    val API_TAG = RuleKey.string("api.tag", StringRuleMode.MERGE_DISTINCT)

    /** Rule key for resolving the API lifecycle status (e.g. "undone", "deprecated"). */
    val API_STATUS = RuleKey.string("api.status")

    /** Rule key for resolving whether an API is open/exposed. */
    val API_OPEN = RuleKey.boolean("api.open")

    /** Rule key for resolving a mock value for a field (e.g. `field.mock`). */
    val FIELD_MOCK = RuleKey.string("field.mock")
}
