package com.itangcent.easyapi.exporter.channel.yapi

import com.itangcent.easyapi.rule.EventRuleMode
import com.itangcent.easyapi.rule.RuleKey

/**
 * YApi-specific rule keys.
 * Extracted from [RuleKeys] so the shared file can be identical between
 * easy-api and easy-yapi.
 *
 * @see com.itangcent.easyapi.rule.RuleKeys for general (shared) rule keys
 */
object YapiRuleKeys {
    val YAPI_PROJECT = RuleKey.string("yapi.project", aliases = listOf("project", "module"))
    val YAPI_EXPORT_BEFORE = RuleKey.event("yapi.export.before", EventRuleMode.THROW_IN_ERROR)
    val YAPI_SAVE_BEFORE   = RuleKey.event("yapi.save.before", EventRuleMode.THROW_IN_ERROR)
    val YAPI_SAVE_AFTER    = RuleKey.event("yapi.save.after", EventRuleMode.THROW_IN_ERROR)
}
