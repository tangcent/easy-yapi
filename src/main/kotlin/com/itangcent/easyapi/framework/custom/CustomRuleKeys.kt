package com.itangcent.easyapi.framework.custom

import com.itangcent.easyapi.core.rule.ContextKind
import com.itangcent.easyapi.core.rule.OutputShape
import com.itangcent.easyapi.core.rule.RuleKey
import com.itangcent.easyapi.core.rule.RuleKeyScheme
import com.itangcent.easyapi.core.rule.binding

/**
 * Custom-framework-specific rule keys.
 *
 * Framework-specific keys live in the framework package (DAG compliance —
 * `core.rule.RuleKeys` owns the shared keys; frameworks own their own).
 * Mirrors the `HoppscotchRuleKeys` / `OpenApiRuleKeys` pattern — `object`
 * with `RuleKey.string(...)` / `RuleKey.boolean(...)` vals;
 * `RuleKey.collectFrom(CustomRuleKeys)` enumerates them via reflection.
 *
 * Consumed by [CustomApiRecognizer.ruleKeys] so [com.itangcent.easyapi.core.rule.RuleKeyRegistry]
 * surfaces them in `list_rule_keys` and `RuleProposalValidator` accepts them.
 *
 * The 13 extraction keys drive class/method recognition, HTTP method, path,
 * and parameter binding. The 5 framework-scoped lifecycle `EventKey`s
 * (`custom.class.parse.before/after`, `custom.method.parse.before/after`,
 * `custom.export.after`) are fired by [CustomClassExporter] alongside the
 * shared hooks (`api.class.parse.before/after`, etc.) because the rule
 * evaluation context does not expose the framework name to user-written
 * rules — these custom hooks give users a framework-scoped surface for
 * side-effect rules that must only run during Custom-framework extraction.
 *
 * @see com.itangcent.easyapi.core.rule.RuleKeys for general (shared) rule keys
 */
object CustomRuleKeys {

    /** `true` if the class is a Custom API class. */
    val CUSTOM_CLASS_IS_API = RuleKey.boolean(
        "custom.class.is.api",
        scheme = RuleKeyScheme(
            summary = "Whether the class is a Custom API class.",
            contextKinds = listOf(ContextKind.CLASS),
            outputShape = OutputShape.BOOLEAN
        )
    )

    /** `true` if the method is a Custom endpoint. */
    val CUSTOM_METHOD_IS_API = RuleKey.boolean(
        "custom.method.is.api",
        scheme = RuleKeyScheme(
            summary = "Whether the method is a Custom endpoint.",
            contextKinds = listOf(ContextKind.METHOD, ContextKind.CLASS),
            outputShape = OutputShape.BOOLEAN
        )
    )

    /** HTTP verb (`GET`/`POST`/...) for the method. */
    val CUSTOM_HTTP_METHOD = RuleKey.string(
        "custom.http.method",
        scheme = RuleKeyScheme(
            summary = "HTTP verb (GET/POST/…) for the method.",
            contextKinds = listOf(ContextKind.METHOD, ContextKind.CLASS),
            outputShape = OutputShape.STRING
        )
    )

    /** Base path (class) / method path (method) — context-sensitive. */
    val CUSTOM_PATH = RuleKey.string(
        "custom.path",
        scheme = RuleKeyScheme(
            summary = "Base path (class) / method path (method) — context-sensitive.",
            contextKinds = listOf(ContextKind.METHOD, ContextKind.CLASS),
            outputShape = OutputShape.STRING
        )
    )

    /** `true` → bind parameter as request body. */
    val CUSTOM_PARAM_AS_JSON_BODY = RuleKey.boolean(
        "custom.param.as.json.body",
        scheme = RuleKeyScheme(
            summary = "Bind the parameter as request body when true.",
            contextKinds = listOf(ContextKind.PARAMETER),
            outputShape = OutputShape.BOOLEAN
        )
    )

    /** `true` → bind parameter as form field. */
    val CUSTOM_PARAM_AS_FORM_BODY = RuleKey.boolean(
        "custom.param.as.form.body",
        scheme = RuleKeyScheme(
            summary = "Bind the parameter as a form field when true.",
            contextKinds = listOf(ContextKind.PARAMETER),
            outputShape = OutputShape.BOOLEAN
        )
    )

    /** `true` → bind parameter as path variable. */
    val CUSTOM_PARAM_AS_PATH_VAR = RuleKey.boolean(
        "custom.param.as.path.var",
        scheme = RuleKeyScheme(
            summary = "Bind the parameter as a path variable when true.",
            contextKinds = listOf(ContextKind.PARAMETER),
            outputShape = OutputShape.BOOLEAN
        )
    )

    /** `true` → bind parameter as cookie. */
    val CUSTOM_PARAM_AS_COOKIE = RuleKey.boolean(
        "custom.param.as.cookie",
        scheme = RuleKeyScheme(
            summary = "Bind the parameter as a cookie when true.",
            contextKinds = listOf(ContextKind.PARAMETER),
            outputShape = OutputShape.BOOLEAN
        )
    )

    /** Path-variable name override. */
    val CUSTOM_PARAM_PATH_VAR = RuleKey.string(
        "custom.param.path.var",
        scheme = RuleKeyScheme(
            summary = "Path-variable name override.",
            contextKinds = listOf(ContextKind.PARAMETER),
            outputShape = OutputShape.STRING
        )
    )

    /** Header name (when binding=header). */
    val CUSTOM_PARAM_HEADER = RuleKey.string(
        "custom.param.header",
        scheme = RuleKeyScheme(
            summary = "Header name (when binding=header).",
            contextKinds = listOf(ContextKind.PARAMETER),
            outputShape = OutputShape.STRING
        )
    )

    /** Cookie name (when binding=cookie). */
    val CUSTOM_PARAM_COOKIE = RuleKey.string(
        "custom.param.cookie",
        scheme = RuleKeyScheme(
            summary = "Cookie name (when binding=cookie).",
            contextKinds = listOf(ContextKind.PARAMETER),
            outputShape = OutputShape.STRING
        )
    )

    /** Cookie value override. */
    val CUSTOM_PARAM_COOKIE_VALUE = RuleKey.string(
        "custom.param.cookie.value",
        scheme = RuleKeyScheme(
            summary = "Cookie value override.",
            contextKinds = listOf(ContextKind.PARAMETER),
            outputShape = OutputShape.STRING
        )
    )

    /** Parameter name override (query/form). */
    val CUSTOM_PARAM_NAME = RuleKey.string(
        "custom.param.name",
        scheme = RuleKeyScheme(
            summary = "Parameter name override (query/form).",
            contextKinds = listOf(ContextKind.PARAMETER),
            outputShape = OutputShape.STRING
        )
    )

    // ── Framework-scoped lifecycle hooks ─────────────────────────
    // Fired by CustomClassExporter alongside the corresponding shared hooks
    // (shared first, then custom). See design Decision 8.

    /** Fired after `api.class.parse.before` — before parsing a class. */
    val CUSTOM_CLASS_PARSE_BEFORE = RuleKey.event(
        "custom.class.parse.before",
        scheme = RuleKeyScheme(
            summary = "Fires before a Custom class is parsed.",
            contextKinds = listOf(ContextKind.CLASS),
            outputShape = OutputShape.EVENT
        )
    )

    /** Fired after `api.class.parse.after` — after parsing a class. */
    val CUSTOM_CLASS_PARSE_AFTER = RuleKey.event(
        "custom.class.parse.after",
        scheme = RuleKeyScheme(
            summary = "Fires after a Custom class is parsed.",
            contextKinds = listOf(ContextKind.CLASS),
            outputShape = OutputShape.EVENT
        )
    )

    /** Fired after `api.method.parse.before` — before parsing a method. */
    val CUSTOM_METHOD_PARSE_BEFORE = RuleKey.event(
        "custom.method.parse.before",
        scheme = RuleKeyScheme(
            summary = "Fires before a Custom method is parsed.",
            contextKinds = listOf(ContextKind.METHOD),
            outputShape = OutputShape.EVENT
        )
    )

    /** Fired after `api.method.parse.after` — after parsing a method. */
    val CUSTOM_METHOD_PARSE_AFTER = RuleKey.event(
        "custom.method.parse.after",
        scheme = RuleKeyScheme(
            summary = "Fires after a Custom method is parsed.",
            contextKinds = listOf(ContextKind.METHOD),
            outputShape = OutputShape.EVENT
        )
    )

    /** Fired after `export.after` — after building each endpoint. */
    val CUSTOM_EXPORT_AFTER = RuleKey.event(
        "custom.export.after",
        scheme = RuleKeyScheme(
            summary = "Fires after a Custom endpoint is built; api exposes export-model mutations.",
            contextKinds = listOf(ContextKind.METHOD, ContextKind.CLASS, ContextKind.EMPTY),
            additionalBindings = listOf(binding("api")),
            outputShape = OutputShape.EVENT,
            dryRunnable = false
        )
    )
}
