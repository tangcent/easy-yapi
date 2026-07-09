package com.itangcent.easyapi.exporter.channel.markdown.template

import com.itangcent.easyapi.exporter.channel.curl.CurlBuildOptions
import com.itangcent.easyapi.exporter.channel.curl.CurlBuilder
import com.itangcent.easyapi.exporter.channel.curl.CurlFormatOptions
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.GrpcMetadata
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.model.ObjectModelVisitTracker

/**
 * Builds the pure-data [TemplateModel] from already-resolved [ApiEndpoint]s.
 *
 * Pure function: no PSI/VFS access, no side effects. Walks the recursive
 * [ObjectModel] bodies into a flat, cycle-safe list of [FieldView]s using
 * [ObjectModelVisitTracker] (cycle-safe by object identity, not depth-capped).
 *
 * The body-flattening logic is ported from the legacy `DefaultMarkdownFormatter`
 * (`formatObjectModelRecursive` / `formatFieldRow` / `formatArrayItemRecursive` /
 * `buildFieldDescription` / `formatType`) so the default template can reproduce the old
 * output byte-for-byte (the parity gate — [MarkdownTemplateParityTest]).
 *
 * The JSON demo is **not** pre-rendered here — `BodyView.asDemo()`/`asJson()`/`asJson5()`
 * are evaluated lazily at render time. See [BodyView].
 *
 * ## Lazy cURL provider
 *
 * [build] accepts an optional [host] + [CurlFormatOptions]; when supplied (or via the
 * defaults — `CurlBuilder.DEFAULT_HOST` + `CurlFormatOptions()`), each [HttpView] carries
 * a lazy [HttpView.curlProvider] that templates can invoke as `{{{api.http.curl()}}}`.
 * The provider invokes the pure [CurlBuilder.format] (no `Project`, no suspend, no
 * scripts). [IdeaLog] is implemented only so the provider can warn on
 * an unexpected formatting failure; the builder itself remains side-effect-free.
 *
 * @see TemplateModel
 */
object TemplateModelBuilder : IdeaLog {

    /**
     * @param endpoints the endpoints to render, already resolved (no PSI reads happen here).
     * @param moduleName the document title passed to `MarkdownChannel.export`.
     * @param host Target host for [HttpView.curl]. Defaults to
     *   [CurlBuilder.DEFAULT_HOST] (`"{{host}}"`) so a bare `build(...)` call — as in
     *   [MarkdownTemplateParityTest] / [TemplateModelBuilderTest] — yields `curl()` output
     *   with the placeholder host (or `null` if the template never invokes it). Blank
     *   string is treated as the default.
     * @param formatOptions Format flags forwarded to [CurlBuilder.format] inside the
     *   `curlProvider`. Defaults to [CurlFormatOptions] (5 flags at their defaults).
     */
    fun build(
        endpoints: List<ApiEndpoint>,
        moduleName: String,
        host: String = CurlBuilder.DEFAULT_HOST,
        formatOptions: CurlFormatOptions = CurlFormatOptions(),
    ): TemplateModel {
        val curlHost = host.takeIf { it.isNotBlank() } ?: CurlBuilder.DEFAULT_HOST
        val groups = endpoints
            .groupBy { it.folder ?: "" }
            .map { (folder, list) -> Group(folder = folder, endpoints = list.map { it.toView(curlHost, formatOptions) }) }
        return TemplateModel(
            moduleName = moduleName,
            groups = groups,
            endpointCount = endpoints.size,
        )
    }

    // ---- Endpoint → view ----

    private fun ApiEndpoint.toView(curlHost: String, formatOptions: CurlFormatOptions): Endpoint {
        val meta = metadata
        // Mirror DefaultMarkdownFormatter: `ep.description?.takeIf { it.isNotBlank() }`
        // so whitespace-only descriptions are treated as absent (parity gate).
        val desc = description?.takeIf { it.isNotBlank() }
        return when (meta) {
            is HttpMetadata -> Endpoint(
                name = name,
                description = desc,
                protocol = meta.protocol,
                path = meta.path,
                method = meta.method.name,
                http = meta.toHttpView(this, curlHost, formatOptions),
                grpc = null,
            )
            is GrpcMetadata -> Endpoint(
                name = name,
                description = desc,
                protocol = meta.protocol,
                path = meta.path,
                method = meta.path.substringAfterLast('/'),
                http = null,
                grpc = meta.toGrpcView(),
            )
        }
    }

    private fun HttpMetadata.toHttpView(
        endpoint: ApiEndpoint,
        curlHost: String,
        formatOptions: CurlFormatOptions,
    ): HttpView {
        val pathParams = parameters.filter { it.binding == ParameterBinding.Path }.map { it.toParam() }
        val queryParams = parameters.filter { it.binding == ParameterBinding.Query }.map { it.toParam() }
        val formParams = parameters.filter { it.binding == ParameterBinding.Form }.map { it.toParam() }
        val headers = headers.map { it.toHeader() }
        val body = body?.toBodyView()
        val response = responseBody?.toBodyView()
        val hasRequestContent = pathParams.isNotEmpty() ||
            queryParams.isNotEmpty() ||
            formParams.isNotEmpty() ||
            headers.isNotEmpty() ||
            body != null
        return HttpView(
            pathParams = pathParams,
            queryParams = queryParams,
            formParams = formParams,
            headers = headers,
            body = body,
            response = response,
            hasRequestContent = hasRequestContent,
            // Lazy cURL; `runPreScripts=false` (pure format only).
            curlProvider = {
                runCatching {
                    CurlBuilder.format(endpoint, curlHost, CurlBuildOptions(format = formatOptions))
                }.onFailure {
                    LOG.warn("curl: failed to build for '${endpoint.name}': ${it.message}", it)
                }.getOrNull()
            },
        )
    }

    private fun GrpcMetadata.toGrpcView(): GrpcView {
        return GrpcView(
            serviceName = serviceName,
            methodName = path.substringAfterLast('/'),
            streamingType = streamingType.name,
            fullPath = path,
            body = body?.toBodyView(),
            response = responseBody?.toBodyView(),
        )
    }

    private fun com.itangcent.easyapi.exporter.model.ApiParameter.toParam(): Param = Param(
        name = name,
        defaultValue = defaultValue ?: "",
        required = required,
        type = type.name.lowercase(),
        description = description ?: "",
    )

    private fun com.itangcent.easyapi.exporter.model.ApiHeader.toHeader(): Header = Header(
        name = name,
        value = value ?: "",
        required = required,
        description = description ?: "",
    )

    // ---- Body flattening (ported from DefaultMarkdownFormatter) ----
    //
    // Produces a flat, cycle-safe list of [FieldView]s. The indent string and desc format
    // match the legacy `Row` byte-for-byte (parity gate — review findings F5, F6):
    //  - depth 0: indent = ""
    //  - depth N>0: indent = "&ensp;&ensp;"×N + "&#124;─"
    //  - desc = comment + options joined with "<br>"

    private fun ObjectModel.toBodyView(): BodyView {
        val fields = mutableListOf<FieldView>()
        val tracker = ObjectModelVisitTracker()
        flattenFieldsInto(fields, this, depth = 0, tracker = tracker)
        return BodyView(model = this, fields = fields)
    }

    private fun flattenFieldsInto(
        fields: MutableList<FieldView>,
        model: ObjectModel,
        depth: Int,
        tracker: ObjectModelVisitTracker,
    ) {
        when (model) {
            is ObjectModel.Object -> {
                if (!tracker.tryEnter(model)) return
                try {
                    for ((fieldName, fieldModel) in model.fields) {
                        appendFieldView(fields, fieldName, fieldModel, depth, tracker)
                    }
                } finally {
                    tracker.exit(model)
                }
            }
            is ObjectModel.Array -> {
                flattenArrayItemIntoFields(fields, model.item, prefix = "[0]", depth = depth, tracker = tracker)
            }
            is ObjectModel.Single -> {
                // Parity (review finding F5): one synthetic row, name="" matching legacy
                // `Row(name="", type=model.type, desc="")` byte-for-byte.
                fields += FieldView(
                    name = "",
                    type = model.type,
                    desc = "",
                    required = false,
                    defaultValue = null,
                    depth = 0,
                    indent = "",
                    hasChildren = false,
                    childrenCount = 0,
                    structuralKind = FieldStructuralKind.PRIMITIVE,
                )
            }
            is ObjectModel.MapModel -> {
                // Parity (review finding F6): two synthetic rows (key + value), matching
                // legacy `Row(name="key", type=formatType(keyType), desc="")` + value byte-for-byte.
                fields += FieldView(
                    name = "key",
                    type = formatType(model.keyType),
                    desc = "",
                    required = false,
                    defaultValue = null,
                    depth = 0,
                    indent = "",
                    hasChildren = false,
                    childrenCount = 0,
                    structuralKind = FieldStructuralKind.MAP,
                )
                fields += FieldView(
                    name = "value",
                    type = formatType(model.valueType),
                    desc = "",
                    required = false,
                    defaultValue = null,
                    depth = 0,
                    indent = "",
                    hasChildren = false,
                    childrenCount = 0,
                    structuralKind = FieldStructuralKind.MAP,
                )
            }
        }
    }

    private fun appendFieldView(
        fields: MutableList<FieldView>,
        fieldName: String,
        fieldModel: FieldModel,
        depth: Int,
        tracker: ObjectModelVisitTracker,
    ) {
        val indent = if (depth > 0) "&ensp;&ensp;".repeat(depth) + "&#124;─" else ""
        val type = formatType(fieldModel.model)
        val desc = buildFieldDescription(fieldModel)
        val structuralKind = structuralKindOf(fieldModel.model)
        val (hasChildren, childrenCount) = childrenInfo(fieldModel.model, tracker)

        fields += FieldView(
            name = fieldName,
            type = type,
            desc = desc,
            required = false,
            defaultValue = null,
            depth = depth,
            indent = indent,
            hasChildren = hasChildren,
            childrenCount = childrenCount,
            structuralKind = structuralKind,
        )

        when (val nested = fieldModel.model) {
            is ObjectModel.Object -> {
                if (tracker.tryEnter(nested)) {
                    try {
                        for ((nestedName, nestedField) in nested.fields) {
                            appendFieldView(fields, nestedName, nestedField, depth + 1, tracker)
                        }
                    } finally {
                        tracker.exit(nested)
                    }
                }
            }
            is ObjectModel.Array -> {
                when (val item = nested.item) {
                    is ObjectModel.Object -> {
                        if (tracker.tryEnter(item)) {
                            try {
                                for ((nestedName, nestedField) in item.fields) {
                                    appendFieldView(fields, nestedName, nestedField, depth + 1, tracker)
                                }
                            } finally {
                                tracker.exit(item)
                            }
                        }
                    }
                    else -> { /* simple array item — no nested rows */ }
                }
            }
            else -> { /* simple — no nested rows */ }
        }
    }

    private fun flattenArrayItemIntoFields(
        fields: MutableList<FieldView>,
        item: ObjectModel,
        prefix: String,
        depth: Int,
        tracker: ObjectModelVisitTracker,
    ) {
        when (item) {
            is ObjectModel.Object -> {
                if (!tracker.tryEnter(item)) return
                try {
                    for ((fieldName, fieldModel) in item.fields) {
                        appendFieldView(fields, "$prefix.$fieldName", fieldModel, depth, tracker)
                    }
                } finally {
                    tracker.exit(item)
                }
            }
            is ObjectModel.Array -> {
                flattenArrayItemIntoFields(fields, item.item, "$prefix[0]", depth, tracker)
            }
            is ObjectModel.Single -> {
                fields += FieldView(
                    name = prefix,
                    type = "${item.type}[]",
                    desc = "",
                    required = false,
                    defaultValue = null,
                    depth = depth,
                    indent = if (depth > 0) "&ensp;&ensp;".repeat(depth) + "&#124;─" else "",
                    hasChildren = false,
                    childrenCount = 0,
                    structuralKind = FieldStructuralKind.PRIMITIVE,
                )
            }
            is ObjectModel.MapModel -> {
                fields += FieldView(
                    name = "$prefix.key",
                    type = formatType(item.keyType),
                    desc = "",
                    required = false,
                    defaultValue = null,
                    depth = depth,
                    indent = if (depth > 0) "&ensp;&ensp;".repeat(depth) + "&#124;─" else "",
                    hasChildren = false,
                    childrenCount = 0,
                    structuralKind = FieldStructuralKind.MAP,
                )
                fields += FieldView(
                    name = "$prefix.value",
                    type = formatType(item.valueType),
                    desc = "",
                    required = false,
                    defaultValue = null,
                    depth = depth,
                    indent = if (depth > 0) "&ensp;&ensp;".repeat(depth) + "&#124;─" else "",
                    hasChildren = false,
                    childrenCount = 0,
                    structuralKind = FieldStructuralKind.MAP,
                )
            }
        }
    }

    /** Returns the structural kind of a model for [FieldView.structuralKind]. */
    private fun structuralKindOf(model: ObjectModel): FieldStructuralKind = when (model) {
        is ObjectModel.Object -> FieldStructuralKind.OBJECT
        is ObjectModel.Array -> FieldStructuralKind.ARRAY
        is ObjectModel.MapModel -> FieldStructuralKind.MAP
        is ObjectModel.Single -> FieldStructuralKind.PRIMITIVE
    }

    /**
     * Returns `(hasChildren, childrenCount)` for a field's model. `hasChildren` is true
     * when the model is an Object/Array<Object>/MapModel that would produce nested rows
     * (subject to cycle-safety). `childrenCount` is the immediate child count (0 for
     * primitives or cycle-blocked nodes).
     */
    private fun childrenInfo(model: ObjectModel, tracker: ObjectModelVisitTracker): Pair<Boolean, Int> {
        return when (model) {
            is ObjectModel.Object -> {
                if (tracker.canEnter(model)) true to model.fields.size
                else false to 0
            }
            is ObjectModel.Array -> {
                when (val item = model.item) {
                    is ObjectModel.Object -> if (tracker.canEnter(item)) true to item.fields.size else false to 0
                    else -> false to 0
                }
            }
            is ObjectModel.MapModel -> true to 2
            is ObjectModel.Single -> false to 0
        }
    }

    /**
     * Mirrors `DefaultMarkdownFormatter.buildFieldDescription`: comment + options joined
     * with `<br>`, options as `value :desc` / `value`.
     */
    private fun buildFieldDescription(fieldModel: FieldModel): String {
        val parts = mutableListOf<String>()
        fieldModel.comment?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        fieldModel.options?.takeIf { it.isNotEmpty() }?.let { options ->
            val optionDesc = options.joinToString("<br>") { opt ->
                if (opt.desc.isNullOrBlank()) "${opt.value}" else "${opt.value} :${opt.desc}"
            }
            parts.add(optionDesc)
        }
        return parts.joinToString("<br>")
    }

    /**
     * Mirrors `DefaultMarkdownFormatter.formatType`: Single→type, Array→`<item>[]`,
     * Object→"object", Map→"map".
     */
    private fun formatType(model: ObjectModel): String = when (model) {
        is ObjectModel.Single -> model.type
        is ObjectModel.Array -> "${formatType(model.item)}[]"
        is ObjectModel.Object -> "object"
        is ObjectModel.MapModel -> "map"
    }
}
