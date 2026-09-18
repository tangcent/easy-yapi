package com.itangcent.easyapi.channel.openapi

import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.ApiParameter
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.HttpMetadata
import com.itangcent.easyapi.core.export.ParameterBinding
import com.itangcent.easyapi.core.export.ParameterType
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.logging.IdeaLog
import com.intellij.openapi.project.Project

/**
 * Pure `List<ApiEndpoint> → OpenApiDocument` converter.
 *
 * Holds [project] only for future rule hooks; v1 ignores it inside [format]
 * (the `openapi.host` rule is evaluated in `OpenApiChannel.export`).
 *
 * The formatter is non-`suspend` and pure — no rule-engine lookup, no I/O, no
 * PSI access. This makes it unit-testable with plain JUnit + mockito-kotlin.
 *
 * Logging follows AGENTS.md §"Logging": per-endpoint skips log at `info`;
 * collisions / path-normalization failures at `warn`. The class
 * implements [IdeaLog] so `LOG.info` / `LOG.warn` mirror to `idea.log` and the
 * CI `AntiPatternGateTest` accepts it.
 */
class OpenApiFormatter(private val project: Project) : IdeaLog {

    /**
     * Builds an [OpenApiDocument] from [endpoints] using the rule-resolved
     * [envelope] for the document envelope (`info`, `servers`).
     *
     * Signature takes [OpenApiEnvelope]
     * (rule-resolved, internal to the channel package) instead of
     * [OpenApiConfig]. The formatter is no longer concerned with
     * `outputFormat` — that's consumed by `OpenApiChannel.export`.
     *
     * Iterates endpoints once. Path collapse is via
     * `PathItemObject.withMethod` (copy-based). gRPC endpoints
     * are defensively skipped here even though `OpenApiChannel.export`
     * filters them first.
     */
    internal fun format(endpoints: List<ApiEndpoint>, envelope: OpenApiEnvelope): OpenApiDocument {
        val usedOperationIds = mutableSetOf<String>()
        val paths = linkedMapOf<String, PathItemObject>()
        val tagOrder = linkedSetOf<String>()
        val schemaConverter = OpenApiSchemaConverter()

        for (endpoint in endpoints) {
            // Defensively skip non-HTTP endpoints. OpenApiChannel.export
            // filters first, but the formatter must be robust to a gRPC endpoint
            // slipping through.
            val meta = endpoint.httpMetadata
            if (meta == null) {
                LOG.info("Skipping non-HTTP endpoint (OpenAPI represents HTTP only): ${endpoint.name ?: "<unknown>"}")
                continue
            }

            // Normalize the path before grouping / emission.
            val normalizedPath = PathNormalizer.normalize(meta.path)
            if (normalizedPath == null) {
                LOG.warn("Skipping endpoint with un-normalizable path: ${meta.path}")
                continue
            }

            // Resolve a document-unique operationId.
            val opIdResult = OperationIdResolver.resolve(meta, endpoint.sourceMethod, usedOperationIds)

            val operation = buildOperation(
                endpoint,
                meta,
                opIdResult.operationId,
                schemaConverter,
                tagOrder,
                normalizedPath,
            )

            // Collapse by normalized path (one PathItem per path).
            // PathItemObject is val-only; withMethod returns a copy.
            val existing = paths.getOrPut(normalizedPath) { PathItemObject() }
            paths[normalizedPath] = existing.withMethod(meta.method, operation)
        }

        return OpenApiDocument(
            info = InfoObject(
                title = envelope.infoTitle,
                version = envelope.infoVersion,
                description = envelope.infoDescription,
            ),
            servers = envelope.serverUrl?.let { listOf(ServerObject(it)) },
            tags = tagOrder.takeIf { it.isNotEmpty() }?.map { TagObject(it) },
            paths = paths,
            components = schemaConverter.buildComponents().takeIf { it.schemas?.isNotEmpty() == true },
        )
    }

    // ─── Operation assembly ────────────────────────────────────────────────

    /**
     * Wires parameters, request body, responses, tags, summary/description,
     * and operationId into a single [OperationObject].
     */
    private fun buildOperation(
        endpoint: ApiEndpoint,
        meta: HttpMetadata,
        opId: String,
        schemaConverter: OpenApiSchemaConverter,
        tagOrder: MutableSet<String>,
        normalizedPath: String,
    ): OperationObject {
        val tag = resolveTag(endpoint, tagOrder)
        val parameters = ensurePathParameters(normalizedPath, buildParameters(meta))
        val requestBody = buildRequestBody(meta, schemaConverter)
        val responses = buildResponses(meta, schemaConverter)

        return OperationObject(
            tags = tag?.let { listOf(it) },
            summary = endpoint.name,
            description = endpoint.description,
            operationId = opId,
            parameters = parameters.takeIf { it.isNotEmpty() },
            requestBody = requestBody,
            responses = responses,
        )
    }

    /**
     * Fills in `in: path` parameters for path-template variables the endpoint
     * did not declare.
     *
     * OAS 3.0 requires every `{var}` in a path template to be defined as a path
     * parameter. Without this, a document containing `/users/{id}` with no
     * matching parameter is rejected by validators and strict consumers — which
     * is what the `OpenApiComplianceTest` check caught in four fixtures. It
     * happens in practice when the variable is contributed by a parent mapping
     * the framework did not surface.
     *
     * A string-typed placeholder keeps the document legal; a declared parameter
     * of the same name always wins, so nothing that already works changes.
     */
    private fun ensurePathParameters(path: String, parameters: List<ParameterObject>): List<ParameterObject> {
        val declared = parameters.filter { it.`in` == "path" }.map { it.name }.toSet()
        val missing = PATH_VARIABLE_PATTERN.findAll(path)
            .map { it.groupValues[1] }
            .filterNot { it in declared }
            .toList()
        if (missing.isEmpty()) return parameters
        return parameters + missing.map { name ->
            ParameterObject(
                name = name,
                `in` = "path",
                required = true,
                schema = SchemaObject(type = "string"),
            )
        }
    }

    /**
     * Resolves the operation's tag: `endpoint.folder` when
     * present and non-blank, else the simple name of `endpoint.className`
     * (last `.`-segment). Returns `null` when neither is available — the
     * operation's `tags` field is then `null` (omitted on the wire).
     *
     * Resolved tags are also added to [tagOrder] so the document-level
     * `tags` array preserves first-appearance order and is
     * deduplicated by name (LinkedHashSet semantics).
     */
    private fun resolveTag(endpoint: ApiEndpoint, tagOrder: MutableSet<String>): String? {
        val tag = endpoint.folder?.takeIf { it.isNotBlank() }
            ?: endpoint.className?.substringAfterLast('.')?.takeIf { it.isNotBlank() }
        if (tag != null) {
            tagOrder.add(tag)
        }
        return tag
    }

    // ─── Parameter mapping ─────────────────────────────────────────

    /**
     * Builds the OAS Parameter Object list for one operation.
     *
     * - Query → `in: query`
     * - Path → `in: path`, `required: true` (OAS mandates path params are required)
     * - Header → `in: header`
     * - Cookie → `in: cookie`
     * - Body / Form / Ignored → dropped — body/form are covered by
     *   the request body; ignored is dropped silently.
     *
     * `ApiHeader` (request headers) are added as `in: header` parameters
     * after the typed parameters, deduplicated by name against any
     * header-typed `ApiParameter` already emitted for the same operation.
     *
     * `example` is populated from `ApiParameter.example` (or `defaultValue`
     * as fallback) when present. `enum` is populated when
     * `ApiParameter.enumValues` is non-empty.
     */
    private fun buildParameters(meta: HttpMetadata): List<ParameterObject> {
        val parameters = mutableListOf<ParameterObject>()
        val seenHeaderNames = mutableSetOf<String>()

        for (param in meta.parameters) {
            val binding = param.binding ?: continue
            val parameterObject = when (binding) {
                is ParameterBinding.Query -> ParameterObject(
                    name = param.name,
                    `in` = "query",
                    required = param.required,
                    description = param.description,
                    schema = schemaForParameter(param),
                    example = param.example ?: param.defaultValue,
                )
                is ParameterBinding.Path -> ParameterObject(
                    name = param.name,
                    `in` = "path",
                    required = true,
                    description = param.description,
                    schema = schemaForParameter(param),
                    example = param.example ?: param.defaultValue,
                )
                is ParameterBinding.Header -> {
                    seenHeaderNames.add(param.name)
                    ParameterObject(
                        name = param.name,
                        `in` = "header",
                        required = param.required,
                        description = param.description,
                        schema = schemaForParameter(param),
                        example = param.example ?: param.defaultValue,
                    )
                }
                is ParameterBinding.Cookie -> ParameterObject(
                    name = param.name,
                    `in` = "cookie",
                    required = param.required,
                    description = param.description,
                    schema = schemaForParameter(param),
                    example = param.example ?: param.defaultValue,
                )
                is ParameterBinding.Body,
                is ParameterBinding.Form,
                is ParameterBinding.Ignored -> continue
            }
            parameters.add(parameterObject)
        }

        // ApiHeader (request headers) become header-typed Parameters,
        // deduplicated against header-typed ApiParameters already emitted.
        for (header in meta.headers) {
            if (header.name in seenHeaderNames) continue
            parameters.add(
                ParameterObject(
                    name = header.name,
                    `in` = "header",
                    required = header.required,
                    description = header.description,
                    schema = SchemaObject(type = "string"),
                    example = header.example ?: header.value,
                ),
            )
        }

        return parameters
    }

    /**
     * Builds the OAS Schema for a parameter. Defaults to `(string, null)`;
     * file-typed parameters use `(string, binary)` — used
     * when an array-of-files needs `items` with the binary shape.
     * `enumValues` is forwarded as the schema's `enum`.
     */
    private fun schemaForParameter(param: ApiParameter): SchemaObject {
        val base = if (param.type == ParameterType.FILE) {
            SchemaObject(type = "string", format = "binary")
        } else {
            SchemaObject(type = "string")
        }
        val enumValues = param.enumValues?.takeIf { it.isNotEmpty() }
        return if (enumValues != null) base.copy(enumValues = enumValues) else base
    }

    // ─── Request body mapping ──────────────────────────────────────

    /**
     * Builds the OAS Request Body Object for one operation. Returns `null`
     * when no body should be emitted.
     *
     * - GET / HEAD / DELETE never carry a request body.
     * - `multipart/form-data` (or any `ParameterType.FILE` param)
     *   → multipart schema with `type: string, format: binary` per file.
     * - `application/x-www-form-urlencoded` with form params →
     *   form-urlencoded schema built from form parameters.
     * - `application/json` with a non-null `HttpMetadata.body` →
     *   JSON schema derived via [OpenApiSchemaConverter].
     * - Body-capable method but no body model and no form params
     *   → `null` (no request body emitted).
     */
    private fun buildRequestBody(
        meta: HttpMetadata,
        schemaConverter: OpenApiSchemaConverter,
    ): RequestBodyObject? {
        // GET / HEAD / DELETE never carry a request body.
        if (meta.method in BODY_SUPPRESSED_METHODS) return null

        val contentType = meta.contentType ?: "application/json"
        val formParams = meta.parameters.filter { it.binding == ParameterBinding.Form }
        val hasFileParam = meta.parameters.any { it.type == ParameterType.FILE }
        val required = formParams.any { it.required }

        // Multipart for files.
        if (contentType.contains("multipart/form-data") || hasFileParam) {
            val schema = buildMultipartSchema(meta)
            return RequestBodyObject(
                content = linkedMapOf("multipart/form-data" to MediaTypeObject(schema = schema)),
                required = required,
            )
        }

        // Form-urlencoded.
        if (contentType.contains("application/x-www-form-urlencoded") && formParams.isNotEmpty()) {
            val schema = buildFormSchema(formParams)
            return RequestBodyObject(
                content = linkedMapOf(
                    "application/x-www-form-urlencoded" to MediaTypeObject(schema = schema),
                ),
                required = required,
            )
        }

        // JSON body.
        if (meta.body != null) {
            val schema = schemaConverter.convert(meta.body, nameHint = meta.bodyAttr)
                ?: SchemaObject(type = "object")
            return RequestBodyObject(
                content = linkedMapOf("application/json" to MediaTypeObject(schema = schema)),
                required = false,
            )
        }

        // Body-capable method but no body / form params.
        return null
    }

    /**
     * Builds the multipart schema from form parameters. Each file-typed
     * parameter emits `type: string, format: binary`.
     */
    private fun buildMultipartSchema(meta: HttpMetadata): SchemaObject {
        val properties = linkedMapOf<String, SchemaObject>()
        val required = mutableListOf<String>()
        for (param in meta.parameters) {
            if (param.binding != ParameterBinding.Form) continue
            val schema = if (param.type == ParameterType.FILE) {
                SchemaObject(type = "string", format = "binary")
            } else {
                SchemaObject(type = "string")
            }
            properties[param.name] = schema
            if (param.required) required.add(param.name)
        }
        return SchemaObject(
            type = "object",
            properties = properties.takeIf { it.isNotEmpty() },
            required = required.takeIf { it.isNotEmpty() },
        )
    }

    /**
     * Builds the form-urlencoded schema from form parameters. Each form
     * parameter becomes a `type: string` property.
     */
    private fun buildFormSchema(formParams: List<ApiParameter>): SchemaObject {
        val properties = linkedMapOf<String, SchemaObject>()
        val required = mutableListOf<String>()
        for (param in formParams) {
            properties[param.name] = SchemaObject(type = "string")
            if (param.required) required.add(param.name)
        }
        return SchemaObject(
            type = "object",
            properties = properties.takeIf { it.isNotEmpty() },
            required = required.takeIf { it.isNotEmpty() },
        )
    }

    // ─── Response mapping ──────────────────────────────────────────

    /**
     * Builds the `responses` map for one operation.
     *
     * - `responseBody` present → `200` with `application/json`
     *   schema derived via [OpenApiSchemaConverter] (using `responseType`
     *   as the name hint so the schema is registered under its simple class
     *   name in components).
     * - `responseType` only, no `responseBody` → `200` with
     *   `description` naming the type, no inline schema.
     * - Neither → `204` "No content", no `content` field.
     *
     * No error responses are synthesized. Every emitted Response
     * Object includes a non-empty `description`.
     */
    private fun buildResponses(
        meta: HttpMetadata,
        schemaConverter: OpenApiSchemaConverter,
    ): LinkedHashMap<String, ResponseObject> {
        val responses = linkedMapOf<String, ResponseObject>()

        when {
            meta.responseBody != null -> {
                // nameHint from responseType so the schema
                // is registered under its simple class name in components.
                val schema = schemaConverter.convert(meta.responseBody, nameHint = meta.responseType)
                    ?: SchemaObject(type = "object")
                responses["200"] = ResponseObject(
                    description = "OK",
                    content = linkedMapOf("application/json" to MediaTypeObject(schema = schema)),
                )
            }
            meta.responseType != null -> {
                // Description naming the type, no inline schema.
                responses["200"] = ResponseObject(
                    description = "Response type: ${meta.responseType}",
                )
            }
            else -> {
                // Void → 204 No content.
                responses["204"] = ResponseObject(
                    description = "No content",
                )
            }
        }

        return responses
    }

    private companion object {
        /** HTTP methods that MUST NOT carry a request body. */
        private val BODY_SUPPRESSED_METHODS = setOf(
            HttpMethod.GET,
            HttpMethod.HEAD,
            HttpMethod.DELETE,
        )

        /** Matches an OAS path-template variable — `{id}` in `/users/{id}`. */
        private val PATH_VARIABLE_PATTERN = Regex("\\{([^/{}]+)}")
    }
}
