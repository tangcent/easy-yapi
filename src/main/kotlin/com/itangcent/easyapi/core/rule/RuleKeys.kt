package com.itangcent.easyapi.core.rule

/**
 * All rule key constants used in configuration files.
 *
 * Each key is typed — [RuleKey.StringKey] for string values, [RuleKey.BooleanKey] for booleans,
 * [RuleKey.EventKey] for side-effect events, [RuleKey.IntKey] for integers.
 *
 * Each key also carries a [RuleKeyScheme] describing how it is evaluated, what
 * PSI context it expects, and what output it is expected to produce — the
 * self-describing contract consumed by the validators, the dry-run engine, and
 * the AI tooling (previously re-derived by name in `RuleScriptContextCatalog`).
 *
 * Evaluate with [com.itangcent.easyapi.core.rule.engine.RuleEngine.evaluate]:
 * ```kotlin
 * val name: String? = engine.evaluate(RuleKeys.API_NAME, psiMethod)
 * val ignore: Boolean = engine.evaluate(RuleKeys.FIELD_IGNORE, psiField)
 * engine.evaluate(RuleKeys.JSON_CLASS_PARSE_BEFORE, psiClass)
 * ```
 */
object RuleKeys {

    // ── API metadata ──────────────────────────────────────────────
    val API_NAME = RuleKey.string(
        "api.name",
        scheme = RuleKeyScheme(
            summary = "API name / title of the endpoint.",
            contextKinds = listOf(ContextKind.METHOD, ContextKind.CLASS),
            outputShape = OutputShape.STRING
        )
    )
    val FOLDER_NAME = RuleKey.string(
        "folder.name",
        scheme = RuleKeyScheme(
            summary = "Folder/group name the endpoint is exported under.",
            contextKinds = listOf(ContextKind.METHOD, ContextKind.CLASS),
            outputShape = OutputShape.STRING
        )
    )
    val IGNORE = RuleKey.boolean(
        "ignore",
        scheme = RuleKeyScheme(
            summary = "Skip the element entirely when true.",
            contextKinds = listOf(ContextKind.CLASS, ContextKind.METHOD, ContextKind.FIELD, ContextKind.PARAMETER),
            outputShape = OutputShape.BOOLEAN
        )
    )

    // ── Method rules ──────────────────────────────────────────────
    val METHOD_DOC = RuleKey.string(
        "method.doc", StringRuleMode.MERGE_DISTINCT,
        scheme = RuleKeyScheme(
            summary = "Method documentation text.",
            contextKinds = listOf(ContextKind.METHOD),
            outputShape = OutputShape.MERGED_STRING
        )
    )
    val CLASS_DOC = RuleKey.string(
        "class.doc", StringRuleMode.MERGE_DISTINCT,
        scheme = RuleKeyScheme(
            summary = "Class documentation text.",
            contextKinds = listOf(ContextKind.CLASS),
            outputShape = OutputShape.MERGED_STRING
        )
    )
    val METHOD_DEFAULT_HTTP = RuleKey.string(
        "method.default.http.method",
        scheme = RuleKeyScheme(
            summary = "Default HTTP method when none is detected.",
            contextKinds = listOf(ContextKind.METHOD),
            outputShape = OutputShape.STRING
        )
    )
    val METHOD_CONTENT_TYPE = RuleKey.string(
        "method.content.type",
        scheme = RuleKeyScheme(
            summary = "Content type of the request.",
            contextKinds = listOf(ContextKind.METHOD),
            outputShape = OutputShape.STRING
        )
    )
    val METHOD_RETURN = RuleKey.string(
        "method.return",
        scheme = RuleKeyScheme(
            summary = "Return type of the method.",
            contextKinds = listOf(ContextKind.METHOD),
            outputShape = OutputShape.STRING
        )
    )
    val METHOD_RETURN_MAIN = RuleKey.string(
        "method.return.main",
        scheme = RuleKeyScheme(
            summary = "Main (unwrapped) return type of the method.",
            contextKinds = listOf(ContextKind.METHOD),
            outputShape = OutputShape.STRING
        )
    )
    val CLASS_PREFIX_PATH = RuleKey.string(
        "class.prefix.path",
        scheme = RuleKeyScheme(
            summary = "Path prefix contributed by the class.",
            contextKinds = listOf(ContextKind.CLASS),
            outputShape = OutputShape.STRING
        )
    )
    val ENDPOINT_PREFIX_PATH = RuleKey.string(
        "endpoint.prefix.path",
        scheme = RuleKeyScheme(
            summary = "Path prefix contributed to the endpoint.",
            contextKinds = listOf(ContextKind.METHOD),
            outputShape = OutputShape.STRING
        )
    )
    val PATH_MULTI = RuleKey.string(
        "path.multi",
        scheme = RuleKeyScheme(
            summary = "Multiple paths for the endpoint.",
            contextKinds = listOf(ContextKind.METHOD),
            outputShape = OutputShape.STRING
        )
    )

    // ── Parameter rules ───────────────────────────────────────────
    val PARAM_NAME = RuleKey.string(
        "param.name",
        scheme = RuleKeyScheme(
            summary = "Parameter name.",
            contextKinds = listOf(ContextKind.PARAMETER),
            outputShape = OutputShape.STRING
        )
    )
    val PARAM_TYPE = RuleKey.string(
        "param.type",
        scheme = RuleKeyScheme(
            summary = "Parameter type.",
            contextKinds = listOf(ContextKind.PARAMETER),
            outputShape = OutputShape.STRING
        )
    )
    val PARAM_REQUIRED = RuleKey.boolean(
        "param.required",
        scheme = RuleKeyScheme(
            summary = "Whether the parameter is required.",
            contextKinds = listOf(ContextKind.PARAMETER),
            outputShape = OutputShape.BOOLEAN
        )
    )
    val PARAM_IGNORE = RuleKey.boolean(
        "param.ignore",
        scheme = RuleKeyScheme(
            summary = "Skip the parameter when true.",
            contextKinds = listOf(ContextKind.PARAMETER),
            outputShape = OutputShape.BOOLEAN
        )
    )
    val PARAM_DEFAULT_VALUE = RuleKey.string(
        "param.default.value",
        scheme = RuleKeyScheme(
            summary = "Default value of the parameter.",
            contextKinds = listOf(ContextKind.PARAMETER),
            outputShape = OutputShape.STRING
        )
    )
    val PARAM_DOC = RuleKey.string(
        "param.doc", StringRuleMode.MERGE_DISTINCT, aliases = listOf("doc.param"),
        scheme = RuleKeyScheme(
            summary = "Parameter documentation text.",
            contextKinds = listOf(ContextKind.PARAMETER),
            outputShape = OutputShape.MERGED_STRING
        )
    )
    val PARAM_HTTP_TYPE = RuleKey.string(
        "param.http.type",
        scheme = RuleKeyScheme(
            summary = "HTTP binding type of the parameter (path/query/body/…).",
            contextKinds = listOf(ContextKind.PARAMETER),
            outputShape = OutputShape.STRING
        )
    )
    val PARAM_DEMO = RuleKey.string(
        "param.demo",
        scheme = RuleKeyScheme(
            summary = "Demo/example value of the parameter.",
            contextKinds = listOf(ContextKind.PARAMETER),
            outputShape = OutputShape.STRING
        )
    )
    val PARAM_MOCK = RuleKey.string(
        "param.mock",
        scheme = RuleKeyScheme(
            summary = "Mock value of the parameter.",
            contextKinds = listOf(ContextKind.PARAMETER),
            outputShape = OutputShape.STRING
        )
    )

    // ── Field rules ───────────────────────────────────────────────
    val FIELD_NAME = RuleKey.string(
        "field.name", aliases = listOf("json.rule.field.name"),
        scheme = RuleKeyScheme(
            summary = "Field name in the exported model.",
            contextKinds = listOf(ContextKind.FIELD, ContextKind.METHOD),
            outputShape = OutputShape.STRING
        )
    )
    val FIELD_NAME_PREFIX = RuleKey.string(
        "field.name.prefix",
        scheme = RuleKeyScheme(
            summary = "Prefix prepended to the field name.",
            contextKinds = listOf(ContextKind.FIELD, ContextKind.METHOD),
            outputShape = OutputShape.STRING
        )
    )
    val FIELD_NAME_SUFFIX = RuleKey.string(
        "field.name.suffix",
        scheme = RuleKeyScheme(
            summary = "Suffix appended to the field name.",
            contextKinds = listOf(ContextKind.FIELD, ContextKind.METHOD),
            outputShape = OutputShape.STRING
        )
    )
    val FIELD_REQUIRED = RuleKey.boolean(
        "field.required",
        scheme = RuleKeyScheme(
            summary = "Whether the field is required.",
            contextKinds = listOf(ContextKind.FIELD, ContextKind.METHOD),
            outputShape = OutputShape.BOOLEAN
        )
    )
    val FIELD_IGNORE = RuleKey.boolean(
        "field.ignore",
        scheme = RuleKeyScheme(
            summary = "Skip (strip) the field from the exported model when true.",
            contextKinds = listOf(ContextKind.FIELD, ContextKind.METHOD),
            outputShape = OutputShape.BOOLEAN
        )
    )
    val FIELD_DEFAULT_VALUE = RuleKey.string(
        "field.default.value",
        scheme = RuleKeyScheme(
            summary = "Default value of the field.",
            contextKinds = listOf(ContextKind.FIELD, ContextKind.METHOD),
            outputShape = OutputShape.STRING
        )
    )
    val FIELD_DOC = RuleKey.string(
        "field.doc", StringRuleMode.MERGE_DISTINCT, aliases = listOf("doc.field"),
        scheme = RuleKeyScheme(
            summary = "Field documentation text.",
            contextKinds = listOf(ContextKind.FIELD, ContextKind.METHOD),
            outputShape = OutputShape.MERGED_STRING
        )
    )
    val FIELD_DEMO = RuleKey.string(
        "field.demo",
        scheme = RuleKeyScheme(
            summary = "Demo/example value of the field.",
            contextKinds = listOf(ContextKind.FIELD, ContextKind.METHOD),
            outputShape = OutputShape.STRING
        )
    )
    val FIELD_MOCK = RuleKey.string(
        "field.mock",
        scheme = RuleKeyScheme(
            summary = "Mock value of the field.",
            contextKinds = listOf(ContextKind.FIELD, ContextKind.METHOD),
            outputShape = OutputShape.STRING
        )
    )
    val FIELD_ORDER = RuleKey.string(
        "field.order",
        scheme = RuleKeyScheme(
            summary = "Ordering position of the field.",
            contextKinds = listOf(ContextKind.FIELD, ContextKind.METHOD),
            outputShape = OutputShape.STRING
        )
    )
    val FIELD_ORDER_WITH = RuleKey.string(
        "field.order.with", StringRuleMode.MERGE,
        scheme = RuleKeyScheme(
            summary = "Compares two object-model members for ordering; a and b are field or method contexts.",
            contextKinds = listOf(ContextKind.FIELD, ContextKind.METHOD),
            additionalBindings = listOf(binding("a", ContextKind.FIELD), binding("b", ContextKind.FIELD)),
            outputShape = OutputShape.STRING,
            dryRunnable = false
        )
    )
    val FIELD_ADVANCED = RuleKey.string(
        "field.advanced", StringRuleMode.MERGE,
        scheme = RuleKeyScheme(
            summary = "Advanced field metadata.",
            contextKinds = listOf(ContextKind.FIELD, ContextKind.METHOD),
            outputShape = OutputShape.MERGED_STRING
        )
    )

    // ── JSON rules ────────────────────────────────────────────────
    val JSON_FIELD_PARSE_BEFORE = RuleKey.event(
        "json.field.parse.before", aliases = listOf("field.parse.before"),
        scheme = RuleKeyScheme(
            summary = "Fires before a field is parsed.",
            contextKinds = listOf(ContextKind.FIELD, ContextKind.METHOD),
            outputShape = OutputShape.EVENT
        )
    )
    val JSON_FIELD_PARSE_AFTER = RuleKey.event(
        "json.field.parse.after", aliases = listOf("field.parse.after"),
        scheme = RuleKeyScheme(
            summary = "Fires after a field is parsed.",
            contextKinds = listOf(ContextKind.FIELD, ContextKind.METHOD),
            outputShape = OutputShape.EVENT
        )
    )
    val JSON_CLASS_PARSE_BEFORE = RuleKey.event(
        "json.class.parse.before",
        scheme = RuleKeyScheme(
            summary = "Fires before a class is parsed.",
            contextKinds = listOf(ContextKind.CLASS),
            outputShape = OutputShape.EVENT
        )
    )
    val JSON_CLASS_PARSE_AFTER = RuleKey.event(
        "json.class.parse.after",
        scheme = RuleKeyScheme(
            summary = "Fires after a class is parsed.",
            contextKinds = listOf(ContextKind.CLASS),
            outputShape = OutputShape.EVENT
        )
    )
    val JSON_ADDITIONAL_FIELD = RuleKey.string(
        "json.additional.field", StringRuleMode.MERGE,
        scheme = RuleKeyScheme(
            summary = "Extra JSON object field injected into the serialized response (e.g. computed totals, envelope metadata).",
            contextKinds = listOf(ContextKind.FIELD, ContextKind.METHOD),
            outputShape = OutputShape.JSON,
            jsonValue = true
        )
    )
    val JSON_RULE_CONVERT = RuleKey.string(
        "json.rule.convert",
        scheme = RuleKeyScheme(
            summary = "Type conversion rule; the resolved type behaves as a class context for scripts.",
            contextKinds = listOf(ContextKind.CLASS),
            outputShape = OutputShape.STRING
        )
    )
    val JSON_UNWRAPPED = RuleKey.boolean(
        "json.unwrapped",
        scheme = RuleKeyScheme(
            summary = "Whether the field is unwrapped into the parent object.",
            contextKinds = listOf(ContextKind.FIELD, ContextKind.METHOD),
            outputShape = OutputShape.BOOLEAN
        )
    )

    // ── API lifecycle events ──────────────────────────────────────
    val API_CLASS_PARSE_BEFORE = RuleKey.event(
        "api.class.parse.before",
        scheme = RuleKeyScheme(
            summary = "Fires before an API class is parsed.",
            contextKinds = listOf(ContextKind.CLASS),
            outputShape = OutputShape.EVENT
        )
    )
    val API_CLASS_PARSE_AFTER = RuleKey.event(
        "api.class.parse.after",
        scheme = RuleKeyScheme(
            summary = "Fires after an API class is parsed.",
            contextKinds = listOf(ContextKind.CLASS),
            outputShape = OutputShape.EVENT
        )
    )
    val API_METHOD_PARSE_BEFORE = RuleKey.event(
        "api.method.parse.before",
        scheme = RuleKeyScheme(
            summary = "Fires before an API method is parsed.",
            contextKinds = listOf(ContextKind.METHOD),
            outputShape = OutputShape.EVENT
        )
    )
    val API_METHOD_PARSE_AFTER = RuleKey.event(
        "api.method.parse.after",
        scheme = RuleKeyScheme(
            summary = "Fires after an API method is parsed.",
            contextKinds = listOf(ContextKind.METHOD),
            outputShape = OutputShape.EVENT
        )
    )
    val API_PARAM_PARSE_BEFORE = RuleKey.event(
        "api.param.parse.before", aliases = listOf("param.before"),
        scheme = RuleKeyScheme(
            summary = "Fires before an API parameter is parsed.",
            contextKinds = listOf(ContextKind.PARAMETER),
            outputShape = OutputShape.EVENT
        )
    )
    val API_PARAM_PARSE_AFTER = RuleKey.event(
        "api.param.parse.after", aliases = listOf("param.after"),
        scheme = RuleKeyScheme(
            summary = "Fires after an API parameter is parsed.",
            contextKinds = listOf(ContextKind.PARAMETER),
            outputShape = OutputShape.EVENT
        )
    )
    val EXPORT_AFTER = RuleKey.event(
        "export.after",
        scheme = RuleKeyScheme(
            summary = "Fires after an API endpoint is built; api exposes export-model mutations and cURL rendering.",
            contextKinds = listOf(ContextKind.METHOD, ContextKind.CLASS, ContextKind.EMPTY),
            additionalBindings = listOf(binding("api")),
            outputShape = OutputShape.EVENT,
            dryRunnable = false
        )
    )

    // ── Additional headers/params ─────────────────────────────────
    val METHOD_ADDITIONAL_HEADER = RuleKey.string(
        "method.additional.header", StringRuleMode.MERGE,
        scheme = RuleKeyScheme(
            summary = "Extra request headers (JSON object).",
            contextKinds = listOf(ContextKind.METHOD),
            outputShape = OutputShape.JSON,
            jsonValue = true
        )
    )
    val METHOD_ADDITIONAL_PARAM = RuleKey.string(
        "method.additional.param", StringRuleMode.MERGE,
        scheme = RuleKeyScheme(
            summary = "Extra request params (JSON object).",
            contextKinds = listOf(ContextKind.METHOD),
            outputShape = OutputShape.JSON,
            jsonValue = true
        )
    )
    val METHOD_ADDITIONAL_RESPONSE_HEADER = RuleKey.string(
        "method.additional.response.header", StringRuleMode.MERGE,
        scheme = RuleKeyScheme(
            summary = "Extra response headers (JSON object).",
            contextKinds = listOf(ContextKind.METHOD),
            outputShape = OutputShape.JSON,
            jsonValue = true
        )
    )

    // ── HTTP call events ──────────────────────────────────────────
    val HTTP_CALL_BEFORE = RuleKey.event(
        "http.call.before",
        scheme = RuleKeyScheme(
            summary = "Runs immediately before an HTTP request is sent; request headers can be mutated.",
            contextKinds = listOf(ContextKind.EMPTY),
            additionalBindings = listOf(binding("request")),
            outputShape = OutputShape.EVENT,
            dryRunnable = false
        )
    )
    val HTTP_CALL_AFTER = RuleKey.event(
        "http.call.after",
        scheme = RuleKeyScheme(
            summary = "Runs after an HTTP response; call response.discard() to request a bounded retry.",
            contextKinds = listOf(ContextKind.EMPTY),
            additionalBindings = listOf(binding("request"), binding("response")),
            outputShape = OutputShape.EVENT,
            dryRunnable = false
        )
    )

    // ── Class recognizer rules ────────────────────────────────────
    val CLASS_IS_CTRL = RuleKey.boolean(
        "class.is.spring.ctrl", aliases = listOf("class.is.ctrl"),
        scheme = RuleKeyScheme(
            summary = "Whether the class is a Spring controller.",
            contextKinds = listOf(ContextKind.CLASS),
            outputShape = OutputShape.BOOLEAN
        )
    )
    val CLASS_IS_FEIGN_CTRL = RuleKey.boolean(
        "class.is.feign.ctrl",
        scheme = RuleKeyScheme(
            summary = "Whether the class is a Feign client.",
            contextKinds = listOf(ContextKind.CLASS),
            outputShape = OutputShape.BOOLEAN
        )
    )
    val CLASS_IS_JAXRS_CTRL = RuleKey.boolean(
        "class.is.jaxrs.ctrl",
        scheme = RuleKeyScheme(
            summary = "Whether the class is a JAX-RS resource.",
            contextKinds = listOf(ContextKind.CLASS),
            outputShape = OutputShape.BOOLEAN
        )
    )
    val CLASS_IS_QUARKUS_CTRL = RuleKey.boolean(
        "class.is.quarkus.ctrl",
        scheme = RuleKeyScheme(
            summary = "Whether the class is a Quarkus controller.",
            contextKinds = listOf(ContextKind.CLASS),
            outputShape = OutputShape.BOOLEAN
        )
    )
    val CLASS_IS_GRPC = RuleKey.boolean(
        "class.is.grpc",
        scheme = RuleKeyScheme(
            summary = "Whether the class is a gRPC service.",
            contextKinds = listOf(ContextKind.CLASS),
            outputShape = OutputShape.BOOLEAN
        )
    )

    // ── Enum rules ────────────────────────────────────────────────
    val ENUM_USE_CUSTOM = RuleKey.string(
        "enum.use.custom",
        scheme = RuleKeyScheme(
            summary = "Custom enum conversion rule.",
            contextKinds = listOf(ContextKind.CLASS),
            outputShape = OutputShape.STRING
        )
    )
    val CONSTANT_FIELD_IGNORE = RuleKey.boolean(
        "constant.field.ignore",
        scheme = RuleKeyScheme(
            summary = "Skip a constant field when true.",
            contextKinds = listOf(ContextKind.FIELD),
            outputShape = OutputShape.BOOLEAN
        )
    )

    // ── Properties rules ──────────────────────────────────────────
    val PROPERTIES_PREFIX = RuleKey.string(
        "properties.prefix",
        scheme = RuleKeyScheme(
            summary = "Prefix for property resolution.",
            contextKinds = listOf(ContextKind.CLASS),
            outputShape = OutputShape.STRING
        )
    )

    // ── Markdown export templates ─────────────────────────────────
    // NOTE: These are document-level config read via `ConfigReader.getFirst(...)`,
    // never evaluated by `RuleEngine` against a PSI element. They live here only so
    // `list_rule_keys` surfaces them.
    //
    // `markdown.template` accepts either a local file path OR a remote http(s) URL —
    // the resolver auto-detects by the `http(s)://` prefix. The separate `.file` and
    // `.url` keys were merged into this single key for simpler configuration.
    val MARKDOWN_TEMPLATE = RuleKey.string(
        "markdown.template",
        scheme = RuleKeyScheme(
            summary = "Markdown template path or remote http(s) URL.",
            staticConfiguration = true,
            dryRunnable = false,
            outputShape = OutputShape.STRING
        )
    )
    val MARKDOWN_TEMPLATE_LANGUAGE = RuleKey.string(
        "markdown.template.language",
        scheme = RuleKeyScheme(
            summary = "Language for the Markdown template.",
            staticConfiguration = true,
            dryRunnable = false,
            outputShape = OutputShape.STRING
        )
    )
}
