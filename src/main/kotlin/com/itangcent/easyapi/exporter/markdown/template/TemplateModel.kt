package com.itangcent.easyapi.exporter.markdown.template

/**
 * The Template Model — the versioned data contract between the plugin and user-authored
 * Markdown templates. Plain data classes with no behavior and no PSI/VFS access (NFR-4).
 *
 * Built by [TemplateModelBuilder] from already-resolved [com.itangcent.easyapi.exporter.model.ApiEndpoint]s
 * and exposed to the template engine as the top-level variables. See
 * [.spec/markdown-template/templates/CONTRACT.md](../../../../../../../../../.spec/markdown-template/templates/CONTRACT.md)
 * for the authoritative contract; this file mirrors it exactly.
 *
 * Contract stability (NFR-3): changes SHALL be additive. Field removals or renames require
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
     * Exists for byte-parity with [com.itangcent.easyapi.exporter.markdown.DefaultMarkdownFormatter]:
     * the formatter always emits the `> REQUEST` banner for an HTTP endpoint and adds one
     * extra trailing newline when the request has no params/headers/form/body. The template
     * language has no boolean-OR over several lists, so this is precomputed. Additive; harmless
     * to other templates.
     */
    val hasRequestContent: Boolean,
)

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
 * - [rows] is the **flat**, cycle-safe list of table rows (the indent prefix is already
 *   applied to [Row.name]); the recursive [com.itangcent.easyapi.psi.model.ObjectModel] tree
 *   is flattened by [TemplateModelBuilder] using [com.itangcent.easyapi.psi.model.ObjectModelVisitTracker]
 *   (cycle-safe by object identity, not depth-capped — Req 1.5).
 * - [demo] is the pre-rendered JSON demo text **without** the code fence, or `null` when
 *   the body is null **or** when demos are disabled
 *   (`DefaultMarkdownFormatter(outputDemo = false)`). The `outputDemo` flag is an input to
 *   the model build, not a template variable — the builder sets `demo = null` for every
 *   body when demos are off, so the template's `{{#if ...demo}}` guards suppress the blocks
 *   unchanged .
 */
data class BodyView(
    val rows: List<Row>,
    val demo: String?,
)

/** A single row in a body table. [name] may include the indent HTML entity prefix. */
data class Row(
    val name: String,
    val type: String,
    val desc: String,
)
