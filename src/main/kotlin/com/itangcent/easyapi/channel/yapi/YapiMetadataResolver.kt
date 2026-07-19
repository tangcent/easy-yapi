package com.itangcent.easyapi.channel.yapi

import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.core.rule.engine.RuleEngine

/**
 * Resolves YApi-specific API metadata (tags, status, open) from PSI via the
 * YApi-specific rule keys in [YapiMetaRuleKeys].
 *
 * These methods were previously on the shared `DocMetadataResolver` but are
 * YApi-specific (no easy-api channel consumes them). They live here so the
 * shared resolver stays minimal and free of any single channel's concerns.
 *
 * Not a service — constructed ad-hoc with the [RuleEngine] by the
 * [YapiMetadataPopulator], since the resolution is only needed during YApi
 * export.
 */
class YapiMetadataResolver(private val engine: RuleEngine) {

    /** Resolves the API tag string (may be comma/newline-separated; callers split it). */
    suspend fun resolveApiTag(method: PsiMethod): String? =
        engine.evaluate(YapiMetaRuleKeys.API_TAG, method)

    /** Resolves the API lifecycle status (e.g. "undone", "deprecated"). */
    suspend fun resolveApiStatus(method: PsiMethod): String? =
        engine.evaluate(YapiMetaRuleKeys.API_STATUS, method)

    /** Resolves whether the API is open/exposed (default `false`). */
    suspend fun isApiOpen(method: PsiMethod): Boolean =
        engine.evaluate(YapiMetaRuleKeys.API_OPEN, method)
}
