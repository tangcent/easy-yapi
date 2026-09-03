package com.itangcent.easyapi.core.rule

import com.itangcent.easyapi.core.rule.RuleKey.Companion.string

/**
 * Fixed configuration keys read by name via [com.itangcent.easyapi.core.config.ConfigReader.getFirst]
 * but not declared as a [RuleKey] constant in [RuleKeys].
 *
 * Each entry documents its call site where it is read. Add new entries here
 * whenever a new `configReader.getFirst("fixed.name")` call is introduced —
 * this keeps the AI tooling and validator in sync without forcing the call site
 * to import [RuleKeys].
 *
 * Dynamic key scans (e.g. `MockRuleLoader` scanning `mock[...]` keys by prefix)
 * are NOT enumerated here — those are open-ended prefixes, not fixed key names.
 *
 * [ImplicitKeyCompletenessTest] guards that every `getFirst("<literal>")`
 * in the source tree is covered here or declared in [RuleKeys].
 */
object ImplicitConfigKeys {
    /**
     * All implicit configuration keys.
     */
    val all: List<RuleKey<*>> = listOf(
        // DefaultPsiClassHelper.maxDeep() / maxElements()
        string(
            "max.deep",
            scheme = RuleKeyScheme(
                summary = "Maximum parse depth for object models.",
                staticConfiguration = true,
                dryRunnable = false,
                outputShape = OutputShape.INT
            )
        ),
        string(
            "max.elements",
            scheme = RuleKeyScheme(
                summary = "Maximum element count for object models.",
                staticConfiguration = true,
                dryRunnable = false,
                outputShape = OutputShape.INT
            )
        ),
        // MarkdownChannel — remote template fetcher tuning
        string(
            "markdown.template.url.ttl.seconds",
            scheme = RuleKeyScheme(
                summary = "TTL (seconds) for the cached remote Markdown template.",
                staticConfiguration = true,
                dryRunnable = false,
                outputShape = OutputShape.INT
            )
        ),
        string(
            "markdown.template.url.max.bytes",
            scheme = RuleKeyScheme(
                summary = "Maximum bytes for a fetched remote Markdown template.",
                staticConfiguration = true,
                dryRunnable = false,
                outputShape = OutputShape.INT
            )
        ),
        // MarkdownChannel — host override for the {{{api.http.curl()}}} placeholder
        string(
            "markdown.curl.host",
            scheme = RuleKeyScheme(
                summary = "Host override for the cURL placeholder in Markdown export.",
                staticConfiguration = true,
                dryRunnable = false,
                outputShape = OutputShape.STRING
            )
        )
    )
}
