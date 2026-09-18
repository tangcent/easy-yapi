package com.itangcent.easyapi.channel.apipost

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.httpMetadata
import java.net.URI
import java.security.SecureRandom

/**
 * Options controlling the native document envelope.
 *
 * @property projectId target ApiPost project; null when writing a file only.
 * @property name envelope name — ApiPost uses the OpenAPI `info.title`.
 * @property host the documented API's origin (`https://api.example.com`),
 *   written to `config.host`. **Never** the ApiPost open-API host.
 * @property basePath the documented API's path prefix (`/v1`), written to
 *   `config.base_path`. Together with [host] this is ApiPost's "前置 URL".
 */
data class ApipostNativeOptions(
    val projectId: String? = null,
    val name: String = "",
    val host: String? = null,
    val basePath: String? = null,
) {
    companion object {
        /**
         * Splits [serverUrl] (the `apipost.server.url` rule value) into
         * origin + path. A blank value yields empty host/base path, which is
         * what the captured document showed for an unconfigured export.
         */
        fun of(projectId: String?, name: String, serverUrl: String?): ApipostNativeOptions {
            val trimmed = serverUrl?.trim()
            if (trimmed.isNullOrEmpty()) return ApipostNativeOptions(projectId, name)
            return try {
                val uri = URI(trimmed)
                val origin = uri.scheme?.let { scheme -> uri.authority?.let { "$scheme://$it" } }
                val path = uri.path?.takeIf { it.isNotBlank() && it != "/" }
                ApipostNativeOptions(projectId, name, origin, path)
            } catch (e: Exception) {
                // Not a parseable URL — treat the whole value as the origin
                // rather than dropping what the user configured.
                ApipostNativeOptions(projectId, name, host = trimmed)
            }
        }
    }
}

/**
 * Converts an OpenAPI 3.0 document into the **ApiPost native envelope**.
 *
 * ## Why this exists
 *
 * ApiPost's server never accepts OpenAPI. The web client converts a Swagger /
 * OpenAPI document into the native shape *in the browser* and posts that — see
 * [ApipostNativeDocument]. The plugin therefore has to perform the same
 * conversion, and this is it.
 *
 * ## Why the input is a String
 *
 * Taking the serialized OAS JSON instead of an `OpenApiDocument` keeps
 * `channel.openapi` types out of this package entirely (NFR-7 / AC-11): the
 * only class allowed to name them is [ApipostOpenApiBridge], which hands over a
 * plain JSON string.
 *
 * ## Ordering contract
 *
 * Models are built before APIs because an API's response schema references a
 * model by its generated id (`#/components/schemas/{model_id}`), and folders
 * are emitted before the APIs nested under them. Callers must not reorder the
 * output lists.
 *
 * Pure and deterministic apart from the generated ids — no rule engine, no PSI,
 * no I/O, so it is unit-testable with plain JUnit.
 */
object ApipostNativeFormatter {

    /** `methods` that are real HTTP operations on an OAS path item. */
    private val HTTP_METHODS = setOf("get", "post", "put", "delete", "patch", "head", "options")

    /**
     * @param oasJson serialized OpenAPI 3.0 document ([ApipostOpenApiBridge.toJson]).
     * @param endpoints the same endpoints the document was built from; used only
     *   to derive a folder name from `className` when an operation carries no
     *   `tags`. Matching is best-effort — an unmatched endpoint simply falls
     *   back to the tag (or to the root folder).
     */
    fun format(
        oasJson: String,
        endpoints: List<ApiEndpoint>,
        options: ApipostNativeOptions,
    ): ApipostNativeDocument {
        val root = JsonParser.parseString(oasJson).asJsonObject

        val info = root.obj("info")
        val title = info?.str("title")?.takeIf { it.isNotBlank() } ?: options.name
        val intro = info?.str("description").orEmpty()

        // Models first: every `$ref` rewrite below needs the model ids.
        // The name registry is per-document — a shared one would keep suffixing
        // names (`Result_2`, `Result_3`, …) on every subsequent export.
        val models = buildModels(root.obj("components")?.obj("schemas"), options, mutableSetOf())

        val apis = buildApis(root.obj("paths"), endpoints, options, models.schemaToModelId)

        return ApipostNativeDocument(
            config = ApipostImportConfig(
                host = options.host.orEmpty(),
                basePath = options.basePath.orEmpty(),
            ),
            name = title,
            projectId = options.projectId,
            intro = intro,
            apis = apis,
            models = models.models,
            importType = ApipostNativeDocument.IMPORT_TYPE_APIPOST,
        )
    }

    // ─── models ─────────────────────────────────────────────────────────────

    private class ModelResult(
        val models: List<ApipostModel>,
        val schemaToModelId: Map<String, String>,
    )

    private fun buildModels(
        schemas: JsonObject?,
        options: ApipostNativeOptions,
        usedNames: MutableSet<String>,
    ): ModelResult {
        val models = mutableListOf<ApipostModel>()

        // Every model hangs off a single `schemas` folder, mirroring
        // `components.schemas` — the capture has exactly this shape.
        val rootId = ApipostIds.next()
        models += ApipostModel(
            modelId = rootId,
            modelType = ApipostModel.MODEL_TYPE_FOLDER,
            description = "schemas",
            parentId = ApipostImportConfig.ROOT_ID,
            projectId = options.projectId,
            name = "schemas",
        )

        val schemaToModelId = LinkedHashMap<String, String>()
        if (schemas == null || schemas.size() == 0) return ModelResult(models, schemaToModelId)

        // Pass 1 — allocate an id and a unique name for every schema, so the
        // second pass can resolve `$ref`s that point forwards as well as back.
        val entries = schemas.entrySet().toList()
        val assigned = entries.map { (schemaName, _) ->
            val id = ApipostIds.next()
            schemaToModelId[schemaName] = id
            id to uniqueName(schemaName, usedNames)
        }

        // Pass 2 — materialize each model with its `$ref`s rewritten to ids.
        entries.forEachIndexed { index, (_, schemaElement) ->
            val (id, name) = assigned[index]
            val schema = (rewriteRefs(schemaElement, schemaToModelId) as? JsonObject) ?: JsonObject()
            models += ApipostModel(
                modelId = id,
                oldModelId = ApipostModel.ref(id),
                modelType = ApipostModel.MODEL_TYPE_MODEL,
                parentId = rootId,
                projectId = options.projectId,
                sort = index,
                schema = schema,
                name = name,
            )
        }
        return ModelResult(models, schemaToModelId)
    }

    /**
     * Deduplicates schema names the way the capture shows ApiPost doing it:
     * a repeated name gains a `_2`, `_3` … suffix. Uniqueness has to be
     * guaranteed by the caller — the server's own renaming is not something to
     * rely on.
     */
    private fun uniqueName(raw: String, usedNames: MutableSet<String>): String {
        if (usedNames.add(raw)) return raw
        var suffix = 2
        while (!usedNames.add("${raw}_$suffix")) suffix++
        return "${raw}_$suffix"
    }

    // ─── apis ───────────────────────────────────────────────────────────────

    private fun buildApis(
        paths: JsonObject?,
        endpoints: List<ApiEndpoint>,
        options: ApipostNativeOptions,
        schemaToModelId: Map<String, String>,
    ): List<ApipostNode> {
        if (paths == null) return emptyList()

        // Collect operations first so the folder ids exist before any API
        // references them via parent_id.
        data class Pending(val path: String, val method: String, val op: JsonObject, val folder: String?)

        val pending = mutableListOf<Pending>()
        for ((path, pathItemElement) in paths.entrySet()) {
            val pathItem = pathItemElement as? JsonObject ?: continue
            for ((methodName, opElement) in pathItem.entrySet()) {
                if (methodName.lowercase() !in HTTP_METHODS) continue
                val op = opElement as? JsonObject ?: continue
                pending += Pending(path, methodName.uppercase(), op, folderNameOf(op, path, methodName, endpoints))
            }
        }

        val folderIds = LinkedHashMap<String, String>()
        for (folderName in pending.mapNotNull { it.folder }.distinct()) {
            folderIds[folderName] = ApipostIds.next()
        }

        val nodes = mutableListOf<ApipostNode>()
        // Folders first — an API's parent_id must resolve.
        folderIds.forEach { (folderName, folderId) ->
            nodes += ApipostNode.folder(
                targetId = folderId,
                name = folderName,
                parentId = ApipostImportConfig.ROOT_ID,
                projectId = options.projectId,
            )
        }
        pending.forEachIndexed { index, item ->
            nodes += ApipostNode.api(
                targetId = ApipostIds.next(),
                name = item.op.str("summary")?.takeIf { it.isNotBlank() } ?: "${item.method} ${item.path}",
                url = item.path,
                method = item.method,
                description = item.op.str("description").orEmpty(),
                parentId = item.folder?.let { folderIds[it] } ?: ApipostImportConfig.ROOT_ID,
                projectId = options.projectId,
                request = buildRequest(item.op, schemaToModelId),
                response = buildResponse(item.op, schemaToModelId),
                tags = item.op.getAsJsonArray("tags")?.mapNotNull { (it as? JsonPrimitive)?.asString },
                sort = index,
            )
        }
        return nodes
    }

    /**
     * Folder a node belongs to: the operation's first tag, else the endpoint's
     * simple class name, else null (root).
     */
    private fun folderNameOf(
        op: JsonObject,
        path: String,
        method: String,
        endpoints: List<ApiEndpoint>,
    ): String? {
        op.getAsJsonArray("tags")
            ?.firstOrNull()
            ?.let { (it as? JsonPrimitive)?.asString }
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val match = endpoints.firstOrNull { endpoint ->
            val meta = endpoint.httpMetadata ?: return@firstOrNull false
            meta.method.name.equals(method, ignoreCase = true) && meta.path == path
        } ?: return null
        return (match.className ?: match.sourceClass?.name)?.substringAfterLast('.')?.takeIf { it.isNotBlank() }
    }

    // ─── request / response ─────────────────────────────────────────────────

    private fun buildRequest(op: JsonObject, schemaToModelId: Map<String, String>): ApipostRequest {
        val header = mutableListOf<ApipostParameter>()
        val query = mutableListOf<ApipostParameter>()
        val cookie = mutableListOf<ApipostParameter>()
        val restful = mutableListOf<ApipostParameter>()

        op.getAsJsonArray("parameters")?.forEach { element ->
            val parameter = element as? JsonObject ?: return@forEach
            val name = parameter.str("name")?.takeIf { it.isNotBlank() } ?: return@forEach
            val schema = parameter.obj("schema")?.let { rewriteRefs(it, schemaToModelId) as? JsonObject }
            val mapped = ApipostParameter(
                key = name,
                notNull = if (parameter.bool("required")) {
                    ApipostParameter.REQUIRED
                } else {
                    ApipostParameter.NOT_NULL
                },
                description = parameter.str("description").orEmpty(),
                fieldType = fieldTypeOf(schema),
                paramId = ApipostIds.next(),
                schema = schema ?: JsonObject(),
            )
            when (parameter.str("in")) {
                "query" -> query += mapped
                "header" -> header += mapped
                "path" -> restful += mapped
                "cookie" -> cookie += mapped
                // `in` is required by OAS; anything else is malformed input and
                // is dropped rather than guessed at.
            }
        }

        return ApipostRequest(
            header = ApipostParameterList(header),
            query = ApipostParameterList(query),
            cookie = ApipostParameterList(cookie),
            restful = ApipostParameterList(restful),
            body = buildBody(op.obj("requestBody"), schemaToModelId),
        )
    }

    /**
     * Only the first media type is mapped: ApiPost's editor drives one body at a
     * time, and `mode` selects it.
     */
    private fun buildBody(requestBody: JsonObject?, schemaToModelId: Map<String, String>): ApipostRequestBody {
        val content = requestBody?.obj("content") ?: return ApipostRequestBody()
        val entry = content.entrySet().firstOrNull() ?: return ApipostRequestBody()
        val mediaType = entry.value as? JsonObject
        val schema = mediaType?.obj("schema")?.let { rewriteRefs(it, schemaToModelId) as? JsonObject }
        val raw = mediaType?.get("example")?.let { example ->
            if (example.isJsonPrimitive) example.asString else example.toString()
        }.orEmpty()

        return ApipostRequestBody(
            mode = bodyModeOf(entry.key),
            parameter = emptyList(),
            raw = raw,
            rawParameter = emptyList(),
            rawSchema = schema,
        )
    }

    private fun buildResponse(op: JsonObject, schemaToModelId: Map<String, String>): ApipostResponse {
        val responses = op.obj("responses") ?: return ApipostResponse()
        val examples = mutableListOf<ApipostExample>()
        var defaultAssigned = false

        responses.entrySet().forEachIndexed { index, (code, element) ->
            val response = element as? JsonObject ?: return@forEachIndexed
            val schema = response.obj("content")
                ?.entrySet()
                ?.firstOrNull()
                ?.value
                ?.asJsonObject
                ?.obj("schema")
                ?.let { rewriteRefs(it, schemaToModelId) as? JsonObject }

            val isSuccess = code.startsWith("2")
            val isDefault = if (isSuccess && !defaultAssigned) {
                defaultAssigned = true
                1
            } else {
                -1
            }

            examples += ApipostExample(
                exampleId = (index + 1).toString(),
                expect = ApipostExpect(
                    name = if (isSuccess) "OK" else "失败",
                    isDefault = isDefault,
                    code = code,
                    schema = schema ?: JsonObject(),
                ),
            )
        }
        return ApipostResponse(example = examples)
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    /**
     * Recursively repoints every `#/components/schemas/{name}` reference at the
     * model's generated id. ApiPost keeps OpenAPI's `$ref` spelling and only
     * swaps the name for an id, so this rewrite is all that is needed.
     */
    private fun rewriteRefs(element: JsonElement, schemaToModelId: Map<String, String>): JsonElement =
        when (element) {
            is JsonObject -> JsonObject().also { copy ->
                element.entrySet().forEach { (key, value) ->
                    if (key == "\$ref" && value.isJsonPrimitive) {
                        val raw = value.asString
                        val name = raw.removePrefix(ApipostModel.REF_PREFIX)
                        val target = if (name != raw) schemaToModelId[name] ?: name else raw
                        copy.add(key, JsonPrimitive("${ApipostModel.REF_PREFIX}$target"))
                    } else {
                        copy.add(key, rewriteRefs(value, schemaToModelId))
                    }
                }
            }

            is JsonArray -> JsonArray().also { copy ->
                element.forEach { copy.add(rewriteRefs(it, schemaToModelId)) }
            }

            else -> element
        }

    private fun fieldTypeOf(schema: JsonObject?): String = when (schema?.str("type")) {
        "string" -> "String"
        "integer" -> "Integer"
        "number" -> "Number"
        "boolean" -> "Boolean"
        "array" -> "Array"
        "object" -> "Object"
        else -> "String"
    }

    private fun bodyModeOf(mediaType: String): String {
        val normalized = mediaType.lowercase()
        return when {
            normalized.contains("json") -> "json"
            normalized.contains("x-www-form-urlencoded") -> "urlencoded"
            normalized.contains("multipart/form-data") -> "form-data"
            normalized.contains("xml") -> "xml"
            normalized.startsWith("text/") -> "text"
            else -> "raw"
        }
    }

    private fun JsonObject.obj(name: String): JsonObject? = get(name) as? JsonObject

    private fun JsonObject.str(name: String): String? =
        (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.asString

    private fun JsonObject.bool(name: String): Boolean = (get(name) as? JsonPrimitive)?.asBoolean ?: false
}

/**
 * Client-side id generator for the envelope's `target_id` / `param_id` /
 * `model_id` fields.
 *
 * ApiPost expects 14 lowercase-hex characters (the capture's ids all are), and
 * `parent_id` / `$ref` chains are only resolvable because the client assigns
 * these consistently within one document. 7 random bytes give 56 bits, which is
 * ample for a single export batch.
 */
internal object ApipostIds {

    private val random = SecureRandom()

    fun next(): String {
        val bytes = ByteArray(7)
        random.nextBytes(bytes)
        return buildString(14) {
            bytes.forEach { append(HEX[(it.toInt() shr 4) and 0x0F]).append(HEX[it.toInt() and 0x0F]) }
        }
    }

    private const val HEX = "0123456789abcdef"
}
