package com.itangcent.easyapi.channel.markdown.template

import com.itangcent.easyapi.core.psi.model.ObjectModel
import com.itangcent.easyapi.format.json.ObjectModelJsonConverter

/**
 * The Template Model — the versioned data contract between the plugin and user-authored
 * Markdown templates. Plain data classes with no behavior and no PSI/VFS access.
 *
 * Built by [TemplateModelBuilder] from already-resolved [com.itangcent.easyapi.core.export.ApiEndpoint]s
 * and exposed to the template engine as the top-level variables.
 *
 * Contract stability: changes SHALL be additive. Field removals or renames require
 * a major-version note.
 */
data class TemplateModel(
    /** The module name passed to MarkdownChannel (e.g. "API Documentation"). */
    val moduleName: String,
    /** Endpoints grouped by folder, in original order. */
    val groups: List<Group>,
    /** Total number of exported endpoints (additive convenience for headers). */
    val endpointCount: Int,
)

/** A folder group of endpoints. `folder` is "" when the endpoint had no folder. */
data class Group(
    val folder: String,
    val endpoints: List<Endpoint>,
)

/**
 * A protocol-agnostic API endpoint. `http` is present only when [protocol] == "HTTP";
 * `grpc` is present only when [protocol] == "gRPC".
 */
data class Endpoint(
    val name: String?,
    val description: String?,
    /** "HTTP" | "gRPC". */
    val protocol: String,
    val path: String,
    /** HTTP method name, or gRPC method name (segment after last '/'). */
    val method: String,
    val http: HttpView?,
    val grpc: GrpcView?,
)

/** HTTP-specific view of an endpoint. */
data class HttpView(
    val pathParams: List<Param>,
    val queryParams: List<Param>,
    val formParams: List<Param>,
    val headers: List<Header>,
    val body: BodyView?,
    val response: BodyView?,
    /**
     * `true` iff any of pathParams/queryParams/headers/formParams/body is present.
     *
     * Exists for byte-parity with [com.itangcent.easyapi.channel.markdown.DefaultMarkdownFormatter]:
     * the formatter always emits the `> REQUEST` banner for an HTTP endpoint and adds one
     * extra trailing newline when the request has no params/headers/form/body. The template
     * language has no boolean-OR over several lists, so this is precomputed. Additive; harmless
     * to other templates.
     */
    val hasRequestContent: Boolean,
    /**
     * Lazy cURL provider. Default `{ null }` so templates that never call
     * `curl()` pay no formatting cost, and so direct construction without this field
     * (existing tests / parity fixtures) renders byte-identically.
     *
     * Computed by [TemplateModelBuilder.toHttpView] from the endpoint + injected host +
     * format options. Markdown renders with `runPreScripts = false`,
     * so the provider invokes the pure [com.itangcent.easyapi.channel.curl.CurlBuilder.format]
     * — no `Project`, no suspend, no scripts.
     */
    val curlProvider: () -> String? = { null },
) {
    /**
     * Template-callable no-arg method: `{{{api.http.curl()}}}`.
     *
     * Returns the cURL command string, or `null` if the provider yields null (e.g.
     * construction without a provider — backward-compat). The template engine
     * stringifies `null` as `""`, so existing templates that don't reference
     * `curl()` are unaffected.
     */
    fun curl(): String? = curlProvider()
}

/** gRPC-specific view of an endpoint. */
data class GrpcView(
    val serviceName: String,
    /** path.substringAfterLast('/'). */
    val methodName: String,
    /** UNARY | SERVER_STREAMING | CLIENT_STREAMING | BIDIRECTIONAL. */
    val streamingType: String,
    val fullPath: String,
    val body: BodyView?,
    val response: BodyView?,
)

/** A request parameter (path / query / form). */
data class Param(
    val name: String,
    /** "" when null (rendered in the "value" column). */
    val defaultValue: String,
    val required: Boolean,
    /** ParameterType.name.lowercase() — "text" / "file". */
    val type: String,
    /** "" when null. */
    val description: String,
)

/** An HTTP header. */
data class Header(
    val name: String,
    /** "" when null. */
    val value: String,
    val required: Boolean,
    /** "" when null. */
    val description: String,
)

/**
 * A request/response body.
 *
 * - [model] is the structured [ObjectModel] tree (the source of truth for a body).
 *   It is **not** directly iterated by templates — the engine has no recursion and
 *   [ObjectModel] is a sealed class not in `getFieldByName`. It is exposed for
 *   method-call rendering (`asDemo()` / `asJson()` / `asJson5()`) and for future
 *   expansion (e.g. partials / macros when added).
 * - [fields] is the **flat, cycle-safe** list of [FieldView]s the template iterates
 *   over for table/list rendering. The recursive `ObjectModel` tree is flattened by
 *   [TemplateModelBuilder] using [com.itangcent.easyapi.core.psi.model.ObjectModelVisitTracker]
 *   (cycle-safe by object identity, not depth-capped). Each [FieldView] carries a
 *   pre-computed [FieldView.indent] so templates can render indented tables without
 *   arithmetic.
 *
 * The three method-call targets — [asDemo], [asJson], [asJson5] — are evaluated
 * lazily at render time (NOT pre-computed in the builder). `asJson` is sugar for
 * `asDemo` (byte-identical output). All three return strings **without** a
 * markdown code fence; the template owns the fence.
 *
 * **Breaking change:** replaces the legacy `BodyView(rows, demo)` shape.
 * Migration: `body.rows` → `{{#each body.fields as f}}…{{/each}}` with `{{{f.indent}}}{{f.name}}`;
 * `body.demo` → `{{{body.asDemo()}}}` (or `asJson5()` for JSON5).
 */
data class BodyView(
    /** The structured body. */
    val model: ObjectModel,
    /** Cycle-safe, pre-flattened, with depth+indent. */
    val fields: List<FieldView>,
) {
    /** Plain JSON, no fence. Always non-empty when called on a non-null BodyView. */
    fun asDemo(): String = ObjectModelJsonConverter.toJson(model)

    /** Byte-identical to [asDemo] (asDemo is sugar for asJson). */
    fun asJson(): String = asDemo()

    /** JSON5 with comments, no fence. */
    fun asJson5(): String = ObjectModelJsonConverter.toJson5(model)
}

/**
 * A single row in a body's flattened [fields] list. Replaces the legacy `Row` —
 * splitting `name`/`indent`/`depth`/`structuralKind` lets the same data drive both
 * table and list renderings, and lets templates branch on structure
 * (`{{#if f.hasChildren}}`).
 *
 * For synthetic rows produced for non-`Object` top-level bodies (Single/MapModel),
 * the non-name/non-type/non-structuralKind fields default to: `desc=""`,
 * `required=false`, `defaultValue=null`, `hasChildren=false`, `childrenCount=0` —
 * matching the legacy `flattenInto` byte-for-byte (review finding F10).
 */
data class FieldView(
    /** Leaf name (no indent prefix). Indent is separate. */
    val name: String,
    /** Formatted type, e.g. "string", "User[]", "object". */
    val type: String,
    /** Comment + options joined with "<br>" (parity with legacy). */
    val desc: String,
    val required: Boolean,
    val defaultValue: String?,
    /** 0 = top-level. */
    val depth: Int,
    /** Pre-computed: "" at depth 0; else "&ensp;&ensp;"×depth + "&#124;─". */
    val indent: String,
    /** For `{{#if f.hasChildren}}` branching. */
    val hasChildren: Boolean,
    val childrenCount: Int,
    /** OBJECT / ARRAY / MAP / PRIMITIVE — lets templates branch on body shape. */
    val structuralKind: FieldStructuralKind,
)

/** Structural classification of a [FieldView], for `{{#if}}` branching in templates. */
enum class FieldStructuralKind { OBJECT, ARRAY, MAP, PRIMITIVE }
