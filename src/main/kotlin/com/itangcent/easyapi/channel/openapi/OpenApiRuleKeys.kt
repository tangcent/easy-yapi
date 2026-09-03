package com.itangcent.easyapi.channel.openapi

import com.itangcent.easyapi.core.rule.ContextKind
import com.itangcent.easyapi.core.rule.EventRuleMode
import com.itangcent.easyapi.core.rule.OutputShape
import com.itangcent.easyapi.core.rule.RuleKey
import com.itangcent.easyapi.core.rule.RuleKeyScheme
import com.itangcent.easyapi.core.rule.binding

/**
 * OpenAPI channel-specific rule keys.
 *
 * These rule keys allow users to customize OpenAPI export behavior via the
 * rule engine (e.g., in `.easy.api.yml` files):
 *
 * | Rule Key | Purpose |
 * |----------|---------|
 * | `openapi.host` | Base URL override for the `servers` array (legacy; mirrored to `openapi.server.url`) |
 * | `openapi.server.url` | Base URL override for the `servers` array (preferred OAS name) |
 * | `openapi.info.title` | Document `info.title` override |
 * | `openapi.info.version` | Document `info.version` override |
 * | `openapi.info.description` | Document `info.description` override |
 * | `openapi.format.after` | Post-format hook (fires after `OpenApiFormatter.format` completes, before serialization) |
 *
 * Channel-specific keys live in the channel package (DAG compliance —
 * `core.rule.RuleKeys` owns the shared keys; channels own their own).
 *
 * Mirrors the [com.itangcent.easyapi.channel.hoppscotch.HoppscotchRuleKeys]
 * pattern — `object` with `RuleKey.string(...)` / `RuleKey.event(...)` vals;
 * `RuleKey.collectFrom(OpenApiRuleKeys)` enumerates them via reflection.
 *
 * Document-metadata rule keys
 * (`OPENAPI_INFO_TITLE` / `OPENAPI_INFO_VERSION` / `OPENAPI_INFO_DESCRIPTION`
 * / `OPENAPI_SERVER_URL`) are evaluated once per export operation (without
 * an element context) inside `OpenApiChannel.export` — the first non-blank
 * result wins. The `openapi.host` key is kept as a legacy alias and is
 * consulted as a fallback for `openapi.server.url`.
 *
 * @see com.itangcent.easyapi.core.rule.RuleKeys for general (shared) rule keys
 */
object OpenApiRuleKeys {
    /**
     * Base URL override for the OpenAPI `servers` array (legacy key).
     *
     * Evaluated against the PSI class/method context. When set, the value
     * populates `servers[0].url` in the emitted document. Kept for backwards
     * compatibility — new configs should prefer [OPENAPI_SERVER_URL]
     * (the OAS-spec-aligned name). `OpenApiChannel.export` consults this key
     * as a fallback when [OPENAPI_SERVER_URL] is blank.
     */
    val OPENAPI_HOST = RuleKey.string(
        "openapi.host",
        scheme = RuleKeyScheme(
            summary = "Base URL override for the OpenAPI servers array (legacy).",
            contextKinds = listOf(ContextKind.CLASS, ContextKind.EMPTY),
            outputShape = OutputShape.STRING
        )
    )

    /**
     * Base URL override for the OpenAPI `servers` array (preferred OAS name).
     *
     * Evaluated once per export operation inside `OpenApiChannel.export`.
     * First non-blank result wins. Falls back to [OPENAPI_HOST], then to
     * settings/options-panel values, then to the built-in default (no
     * `servers` array emitted).
     */
    val OPENAPI_SERVER_URL = RuleKey.string(
        "openapi.server.url",
        scheme = RuleKeyScheme(
            summary = "Base URL override for the OpenAPI servers array.",
            contextKinds = listOf(ContextKind.EMPTY),
            outputShape = OutputShape.STRING
        )
    )

    /**
     * Document `info.title` override.
     *
     * Evaluated once per export operation inside `OpenApiChannel.export`.
     * First non-blank result wins. Precedence: options-panel > this rule >
     * persistent settings > project name > `"API"` default.
     */
    val OPENAPI_INFO_TITLE = RuleKey.string(
        "openapi.info.title",
        scheme = RuleKeyScheme(
            summary = "OpenAPI document info.title override.",
            contextKinds = listOf(ContextKind.EMPTY),
            outputShape = OutputShape.STRING
        )
    )

    /**
     * Document `info.version` override.
     *
     * Evaluated once per export operation inside `OpenApiChannel.export`.
     * First non-blank result wins. Precedence: options-panel > this rule >
     * persistent settings > `"1.0.0"` default.
     */
    val OPENAPI_INFO_VERSION = RuleKey.string(
        "openapi.info.version",
        scheme = RuleKeyScheme(
            summary = "OpenAPI document info.version override.",
            contextKinds = listOf(ContextKind.EMPTY),
            outputShape = OutputShape.STRING
        )
    )

    /**
     * Document `info.description` override.
     *
     * Evaluated once per export operation inside `OpenApiChannel.export`.
     * First non-blank result wins. Precedence: options-panel > this rule >
     * persistent settings > `null` (omitted on the wire).
     */
    val OPENAPI_INFO_DESCRIPTION = RuleKey.string(
        "openapi.info.description",
        scheme = RuleKeyScheme(
            summary = "OpenAPI document info.description override.",
            contextKinds = listOf(ContextKind.EMPTY),
            outputShape = OutputShape.STRING
        )
    )

    /**
     * Post-format hook.
     *
     * Fires after `OpenApiFormatter.format(...)` completes and before
     * `OpenApiSerializer.toJson`/`toYaml` serializes the document —
     * scripts can mutate the in-memory `OpenApiDocument` (e.g., inject
     * vendor extensions, scrub a field, sort tags).
     *
     * Uses [EventRuleMode.THROW_IN_ERROR] so a failing script surfaces
     * the exception instead of being silently swallowed (matches the
     * `hopp.format.after` / `postman.format.after` semantics).
     */
    val OPENAPI_FORMAT_AFTER = RuleKey.event(
        "openapi.format.after", EventRuleMode.THROW_IN_ERROR,
        scheme = RuleKeyScheme(
            summary = "Runs after the OpenAPI document is built and before it is serialized.",
            contextKinds = listOf(ContextKind.METHOD),
            additionalBindings = listOf(binding("document")),
            outputShape = OutputShape.EVENT,
            dryRunnable = false
        )
    )
}
