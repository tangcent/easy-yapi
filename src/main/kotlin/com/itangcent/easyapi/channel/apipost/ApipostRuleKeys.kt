package com.itangcent.easyapi.channel.apipost

import com.itangcent.easyapi.core.rule.ContextKind
import com.itangcent.easyapi.core.rule.EventRuleMode
import com.itangcent.easyapi.core.rule.OutputShape
import com.itangcent.easyapi.core.rule.RuleKey
import com.itangcent.easyapi.core.rule.RuleKeyScheme
import com.itangcent.easyapi.core.rule.binding

/**
 * ApiPost-specific rule keys, in the `apipost.*` namespace.
 *
 * Declared the same way as [com.itangcent.easyapi.channel.yapi.YapiRuleKeys]:
 * every key carries a self-describing [RuleKeyScheme] so the AI tooling and the
 * validators read its semantics back instead of inferring them from the name.
 *
 * ## The `host` / `server.url` split (do not merge these)
 *
 * Two different URLs are in play during an export, and conflating them is a
 * real bug that was caught in review:
 *  - [APIPOST_HOST] is **where the ApiPost server lives** — it only ever
 *    assembles `/open/apis/…` request URLs.
 *  - [APIPOST_SERVER_URL] is **the API being documented** — it becomes the
 *    document's `config.host` / `config.base_path` ("前置 URL" in the ApiPost UI).
 *
 * If the former leaked into the latter, every imported endpoint would end up
 * with ApiPost's own domain as its base URL.
 */
object ApipostRuleKeys {

    /** Overrides the target project id for the whole export. */
    val APIPOST_PROJECT = RuleKey.string(
        "apipost.project",
        scheme = RuleKeyScheme(
            summary = "ApiPost target project id; overrides the configured project for this export.",
            contextKinds = listOf(ContextKind.METHOD, ContextKind.CLASS),
            outputShape = OutputShape.STRING
        )
    )

    /** Overrides the ApiPost open-API base URL. */
    val APIPOST_HOST = RuleKey.string(
        "apipost.host",
        scheme = RuleKeyScheme(
            summary = "ApiPost open API base URL override; used ONLY to assemble /open/apis/* requests.",
            contextKinds = listOf(ContextKind.CLASS, ContextKind.EMPTY),
            outputShape = OutputShape.STRING
        )
    )

    /** The documented API's own base URL. */
    val APIPOST_SERVER_URL = RuleKey.string(
        "apipost.server.url",
        scheme = RuleKeyScheme(
            summary = "Base URL of the API being exported; written to the document's host/base_path " +
                "(ApiPost's pre-URL). Left empty when unset. Never derived from apipost.host.",
            contextKinds = listOf(ContextKind.CLASS, ContextKind.EMPTY),
            outputShape = OutputShape.STRING
        )
    )

    /** Fires once, before the batch starts. */
    val APIPOST_EXPORT_BEFORE = RuleKey.event(
        "apipost.export.before", EventRuleMode.THROW_IN_ERROR,
        scheme = RuleKeyScheme(
            summary = "Fires once before an ApiPost export starts.",
            contextKinds = listOf(ContextKind.EMPTY),
            outputShape = OutputShape.EVENT
        )
    )

    /** Fires before each API is pushed; the node can be mutated. */
    val APIPOST_SAVE_BEFORE = RuleKey.event(
        "apipost.save.before", EventRuleMode.THROW_IN_ERROR,
        scheme = RuleKeyScheme(
            summary = "Fires before an endpoint is pushed to ApiPost; the `document` binding can be mutated.",
            contextKinds = listOf(ContextKind.EMPTY),
            additionalBindings = listOf(binding("document")),
            outputShape = OutputShape.EVENT,
            dryRunnable = false
        )
    )

    /** Fires after each API is pushed; the result is exposed. */
    val APIPOST_SAVE_AFTER = RuleKey.event(
        "apipost.save.after", EventRuleMode.THROW_IN_ERROR,
        scheme = RuleKeyScheme(
            summary = "Fires after an endpoint is pushed to ApiPost; `content` and `result` are exposed.",
            contextKinds = listOf(ContextKind.EMPTY),
            additionalBindings = listOf(binding("content"), binding("result")),
            outputShape = OutputShape.EVENT,
            dryRunnable = false
        )
    )
}
