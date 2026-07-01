package com.itangcent.easyapi.exporter.markdown.template

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.GrpcMetadata
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.model.ObjectModelJsonConverter
import com.itangcent.easyapi.psi.model.ObjectModelVisitTracker

/**
 * Builds the pure-data [TemplateModel] from already-resolved [ApiEndpoint]s.
 *
 * Pure function: no PSI/VFS access (NFR-4), no side effects. Walks the recursive
 * [ObjectModel] bodies into a flat, cycle-safe list of [Row]s using
 * [ObjectModelVisitTracker] (cycle-safe by object identity, not depth-capped — Req 1.5),
 * and pre-renders the JSON `demo` via [ObjectModelJsonConverter.toJson].
 *
 * The body-flattening logic is ported from the legacy `DefaultMarkdownFormatter`
 * (`formatObjectModelRecursive` / `formatFieldRow` / `formatArrayItemRecursive` /
 * `buildFieldDescription` / `formatType`) so the default template can reproduce the old
 * output byte-for-byte (the parity gate — [MarkdownTemplateParityTest]).
 *
 * @see TemplateModel
 */
object TemplateModelBuilder {

    /**
     * @param endpoints the endpoints to render, already resolved (no PSI reads happen here).
     * @param outputDemo when `false`, every [BodyView.demo] is set to `null` so the template's
     *   `{{#if ...demo}}` guards suppress the blocks unchanged .
     * @param moduleName the document title passed to `MarkdownChannel.export`.
     */
    fun build(
        endpoints: List<ApiEndpoint>,
        outputDemo: Boolean,
        moduleName: String,
    ): TemplateModel {
        val groups = endpoints
            .groupBy { it.folder ?: "" }
            .map { (folder, list) -> Group(folder = folder, endpoints = list.map { it.toView(outputDemo) }) }
        return TemplateModel(
            moduleName = moduleName,
            groups = groups,
            endpointCount = endpoints.size,
        )
    }

    // ---- Endpoint → view ----

    private fun ApiEndpoint.toView(outputDemo: Boolean): Endpoint {
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
                http = meta.toHttpView(outputDemo),
                grpc = null,
            )
            is GrpcMetadata -> Endpoint(
                name = name,
                description = desc,
                protocol = meta.protocol,
                path = meta.path,
                method = meta.path.substringAfterLast('/'),
                http = null,
                grpc = meta.toGrpcView(outputDemo),
            )
        }
    }

    private fun HttpMetadata.toHttpView(outputDemo: Boolean): HttpView {
        val pathParams = parameters.filter { it.binding == ParameterBinding.Path }.map { it.toParam() }
        val queryParams = parameters.filter { it.binding == ParameterBinding.Query }.map { it.toParam() }
        val formParams = parameters.filter { it.binding == ParameterBinding.Form }.map { it.toParam() }
        val headers = headers.map { it.toHeader() }
        val body = body?.toBodyView(outputDemo)
        val response = responseBody?.toBodyView(outputDemo)
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
        )
    }

    private fun GrpcMetadata.toGrpcView(outputDemo: Boolean): GrpcView {
        return GrpcView(
            serviceName = serviceName,
            methodName = path.substringAfterLast('/'),
            streamingType = streamingType.name,
            fullPath = path,
            body = body?.toBodyView(outputDemo),
            response = responseBody?.toBodyView(outputDemo),
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

    private fun ObjectModel.toBodyView(outputDemo: Boolean): BodyView {
        val rows = mutableListOf<Row>()
        val tracker = ObjectModelVisitTracker()
        flattenInto(rows, this, depth = 0, tracker = tracker)
        val demo = if (outputDemo) ObjectModelJsonConverter.toJson(this) else null
        return BodyView(rows = rows, demo = demo)
    }

    private fun flattenInto(
        rows: MutableList<Row>,
        model: ObjectModel,
        depth: Int,
        tracker: ObjectModelVisitTracker,
    ) {
        when (model) {
            is ObjectModel.Object -> {
                if (!tracker.tryEnter(model)) return
                try {
                    for ((fieldName, fieldModel) in model.fields) {
                        appendFieldRow(rows, fieldName, fieldModel, depth, tracker)
                    }
                } finally {
                    tracker.exit(model)
                }
            }
            is ObjectModel.Array -> {
                flattenArrayItemInto(rows, model.item, prefix = "[0]", depth = depth, tracker = tracker)
            }
            is ObjectModel.Single -> {
                rows += Row(name = "", type = model.type, desc = "")
            }
            is ObjectModel.MapModel -> {
                rows += Row(name = "key", type = formatType(model.keyType), desc = "")
                rows += Row(name = "value", type = formatType(model.valueType), desc = "")
            }
        }
    }

    private fun appendFieldRow(
        rows: MutableList<Row>,
        fieldName: String,
        fieldModel: FieldModel,
        depth: Int,
        tracker: ObjectModelVisitTracker,
    ) {
        val indent = if (depth > 0) "&ensp;&ensp;".repeat(depth) + "&#124;─" else ""
        val type = formatType(fieldModel.model)
        val desc = buildFieldDescription(fieldModel)
        rows += Row(name = "$indent$fieldName", type = type, desc = desc)

        when (val nested = fieldModel.model) {
            is ObjectModel.Object -> {
                if (tracker.tryEnter(nested)) {
                    try {
                        for ((nestedName, nestedField) in nested.fields) {
                            appendFieldRow(rows, nestedName, nestedField, depth + 1, tracker)
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
                                    appendFieldRow(rows, nestedName, nestedField, depth + 1, tracker)
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

    private fun flattenArrayItemInto(
        rows: MutableList<Row>,
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
                        appendFieldRow(rows, "$prefix.$fieldName", fieldModel, depth, tracker)
                    }
                } finally {
                    tracker.exit(item)
                }
            }
            is ObjectModel.Array -> {
                flattenArrayItemInto(rows, item.item, "$prefix[0]", depth, tracker)
            }
            is ObjectModel.Single -> {
                rows += Row(name = prefix, type = "${item.type}[]", desc = "")
            }
            is ObjectModel.MapModel -> {
                rows += Row(name = "$prefix.key", type = formatType(item.keyType), desc = "")
                rows += Row(name = "$prefix.value", type = formatType(item.valueType), desc = "")
            }
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
