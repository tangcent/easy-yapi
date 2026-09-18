package com.itangcent.easyapi.channel.apipost

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.http.HttpClient
import com.itangcent.easyapi.core.http.HttpClientProvider
import com.itangcent.easyapi.core.http.HttpRequestBuilder
import com.itangcent.easyapi.core.http.HttpResponse
import com.itangcent.easyapi.core.http.get
import com.itangcent.easyapi.core.http.post
import com.itangcent.easyapi.core.logging.IdeaLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [ApipostApiClient] backed by ApiPost's real open API.
 *
 * Thin on purpose: it assembles the request body, sends it and classifies the
 * answer. Everything else — ordering, dedup, `$ref` rewriting — belongs to
 * [ApipostExporter], because it is policy rather than transport.
 *
 * ## Wire contract, all of it verified against a live token
 *
 * - **snake_case only.** `targetType` / `TargetType` are rejected as missing
 *   (`REVIEW.md §6.9.1`), so the payload is exactly [ApipostNode] /
 *   [ApipostModel] as [ApipostNativeSerializer] serializes them.
 * - **Success lives in the body.** An existing route answers `HTTP 200` even for
 *   a bad token or a missing field; only an unknown route answers `404`
 *   (`REVIEW.md §6.8.2`). So [HttpResponse.code] is carried for diagnostics and
 *   never decides anything on its own.
 * - **`project_code` is not sent at all.** The earlier reading of it as required
 *   came from misreading the error message, which lists every known required
 *   field rather than only the missing one (`REVIEW.md §6.9.1`).
 *
 * @param baseUrl ApiPost cloud host; trailing slashes are normalised by
 *        [ApipostUrls.normalizeBaseUrl].
 * @param token the user's `api-token`. Never logged — see [describeFailure].
 */
class DefaultApipostApiClient(
    private val baseUrl: String,
    private val token: String,
    private val httpClient: HttpClient,
) : ApipostApiClient, IdeaLog {

    // region listing

    override suspend fun listTeams(): ApipostSyncResult<List<ApipostTeam>> =
        getJson(ApipostUrls.listTeamsUrl(baseUrl)).map { json ->
            json.items().mapNotNull { it.asObject().toTeam() }
        }

    override suspend fun listProjects(teamId: String): ApipostSyncResult<List<ApipostProject>> =
        getJson(ApipostUrls.listProjectsUrl(baseUrl)) { query(TEAM_ID, teamId) }.map { json ->
            json.items().mapNotNull { it.asObject().toProject() }
        }

    override suspend fun listApis(projectId: String): ApipostSyncResult<List<ApipostExistingApi>> =
        getJson(ApipostUrls.listApisUrl(baseUrl)) { query(PROJECT_ID, projectId) }.map { json ->
            json.items().mapNotNull { it.asObject().toExistingApi() }
        }

    override suspend fun listModels(projectId: String): ApipostSyncResult<List<ApipostExistingModel>> =
        getJson(ApipostUrls.listModelsUrl(baseUrl)) { query(PROJECT_ID, projectId) }.map { json ->
            json.items().mapNotNull { it.asObject().toExistingModel() }
        }

    // region writing

    override suspend fun createApi(projectId: String, node: ApipostNode): ApipostSyncResult<String> =
        postJson(ApipostUrls.createApiUrl(baseUrl), node.copy(projectId = projectId)).map { json ->
            // Falls back to the node's own id only because the server is known to
            // adopt a client-supplied `target_id` verbatim (verified twice for APIs
            // and folders); the read-back is what makes a folder that the server
            // already had — and thus had its own id for — still link its children.
            json.obj(DATA)?.str(TARGET_ID) ?: node.targetId
        }

    override suspend fun updateApi(
        projectId: String,
        targetId: String,
        node: ApipostNode,
    ): ApipostSyncResult<Unit> =
        // The whole node goes out, including `method` and `name`: the update
        // contract requires both, and there is no trimming of it the way create
        // allows (`REVIEW.md §6.9.1`).
        postJson(ApipostUrls.updateApiUrl(baseUrl), node.copy(projectId = projectId, targetId = targetId))
            .map { }

    override suspend fun createModel(projectId: String, model: ApipostModel): ApipostSyncResult<String> =
        postJson(ApipostUrls.createModelUrl(baseUrl), model.copy(projectId = projectId))
            // Deliberately no fallback to `model.modelId`: the server *ignores* a
            // client-supplied `model_id` and always mints its own
            // (`REVIEW.md §6.9.5`), so echoing ours back would plant a `$ref` that
            // resolves to nothing. Absent id ⇒ failure, not a silently broken ref.
            .map { json -> json.obj(DATA)?.str(MODEL_ID) }
            .requireId("create model '${model.name}'")

    override suspend fun updateModel(
        projectId: String,
        modelId: String,
        model: ApipostModel,
    ): ApipostSyncResult<String> {
        val payload = ApipostNativeSerializer.payloadObject(model).apply {
            addProperty(MODEL_ID, modelId)
            addProperty(PROJECT_ID, projectId)
            // `original_name` is not a property of the model — it is the name the
            // server should look *for*. Dedup matched on name, so the server-side
            // name is still `model.name`.
            addProperty(ORIGINAL_NAME, model.name)
        }
        return postJson(ApipostUrls.updateModelUrl(baseUrl), payload).map { modelId }
    }

    // region plumbing

    private suspend fun getJson(
        url: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ): ApipostSyncResult<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            httpClient.get {
                this.url = url
                header(TOKEN_HEADER, token)
                block()
            }
        }.fold(::parse, ::describeFailure)
    }

    private suspend fun postJson(url: String, payload: Any): ApipostSyncResult<JsonObject> =
        withContext(Dispatchers.IO) {
            runCatching {
                httpClient.post {
                    this.url = url
                    contentType = CONTENT_TYPE_JSON
                    body = ApipostNativeSerializer.payload(payload)
                    header(TOKEN_HEADER, token)
                }
            }.fold(::parse, ::describeFailure)
        }

    /**
     * Classifies a response body.
     *
     * A body that will not parse means the route is not there — an unknown path
     * answers `404 page not found` as HTML, not JSON (`REVIEW.md §6.8.3`) — so the
     * HTTP status is folded into the message rather than dropped.
     */
    private fun parse(res: HttpResponse): ApipostSyncResult<JsonObject> {
        val body = res.body
        val json = body?.takeIf { it.isNotBlank() }
            ?.let { runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull() }
            ?: return ApipostSyncResult.Failure(
                message = "ApiPost returned an unreadable body (HTTP ${res.code})",
                httpCode = res.code,
            )

        val code = json.int(CODE)
        if (code != ApipostCodes.OK) {
            return ApipostSyncResult.Failure(
                message = json.str(MSG) ?: "ApiPost returned code=$code",
                code = code,
                httpCode = res.code,
            )
        }
        return ApipostSyncResult.Success(json)
    }

    /**
     * Turns a transport exception into a [ApipostSyncResult.Failure].
     *
     * No `catch`/rethrow and no token in the message: the token travels in a
     * header and must never reach a log or a notification.
     */
    private fun describeFailure(cause: Throwable): ApipostSyncResult.Failure {
        LOG.warn("ApipostApiClient: request failed — ${cause.javaClass.simpleName}: ${cause.message}")
        return ApipostSyncResult.Failure(message = cause.message ?: cause.javaClass.simpleName)
    }

    companion object {
        private const val TOKEN_HEADER = "api-token"
        private const val CONTENT_TYPE_JSON = "application/json"

        private const val TARGET_ID = "target_id"
        private const val MODEL_ID = "model_id"
        private const val ORIGINAL_NAME = "original_name"
        private const val CODE = "code"
        private const val MSG = "msg"
    }
}

/**
 * Builds a real [DefaultApipostApiClient] for one export.
 *
 * A factory rather than a direct construction so [ApipostChannel] stays
 * constructible by the extension point (no-arg constructor) while tests inject a
 * fake. The HTTP client comes from [HttpClientProvider], which means the push
 * inherits the user's configured client, timeout and SSL settings — and gets the
 * same request logging every other channel has.
 */
class DefaultApipostApiClientFactory : ApipostApiClientFactory {

    override fun create(baseUrl: String, token: String, project: Project): ApipostApiClient =
        DefaultApipostApiClient(
            baseUrl = baseUrl,
            token = token,
            httpClient = HttpClientProvider.getInstance(project).getClient(),
        )
}

// region result plumbing

/**
 * Transforms a successful payload, passing a failure straight through.
 *
 * Relies on [ApipostSyncResult.Failure] being an `ApipostSyncResult<Nothing>`, so
 * a failure of any type is a failure of every type.
 */
private inline fun <T> ApipostSyncResult<JsonObject>.map(transform: (JsonObject) -> T): ApipostSyncResult<T> =
    when (this) {
        is ApipostSyncResult.Success -> ApipostSyncResult.Success(transform(data))
        is ApipostSyncResult.Failure -> this
    }

/**
 * Keeps a `map { … }` that can return `null` out of the success path.
 *
 * [DefaultApipostApiClient.createModel] returns `null` when the server did not
 * report a `model_id`, which is not a success — there would be nothing to point
 * a `$ref` at.
 */
private fun ApipostSyncResult<String?>.requireId(action: String): ApipostSyncResult<String> = when (this) {
    is ApipostSyncResult.Success -> data
        ?.let { ApipostSyncResult.Success(it) }
        ?: ApipostSyncResult.Failure("ApiPost did not return an id for $action")

    is ApipostSyncResult.Failure -> this
}

// endregion

// region JsonObject reading

/**
 * Field names shared by the request payloads above and the response mapping
 * below.
 *
 * File-private rather than companion-private: the mappers in this region are
 * top-level functions, which cannot see a class's `private` members.
 */
private const val DATA = "data"
private const val LIST_KEY = "list"
private const val NAME = "name"
private const val PROJECT_ID = "project_id"
private const val TEAM_ID = "team_id"

/**
 * Null-tolerant readers.
 *
 * Every field here is optional from the client's point of view — a response shape
 * change must degrade one node, not throw out of a `suspend` function and out of
 * the export (NFR-1 / AC-9). `JsonObject.get` returning `null` is not enough:
 * a field can be present with the wrong JSON type.
 */
private fun JsonObject.str(key: String): String? = (get(key) as? JsonPrimitive)?.asString

private fun JsonObject.int(key: String): Int? = (get(key) as? JsonPrimitive)?.asInt

private fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject

/**
 * The rows of any list route's response — call this on the **envelope**, not on
 * `data`.
 *
 * Two shapes are in use, both measured against the live API on 2026-09-17 and
 * archived in `reference/apipost-list-shapes.md`:
 *
 * | routes | `data` holds |
 * |---|---|
 * | `/open/team/list`, `/open/project/list` | a **bare array** |
 * | `/open/apis/list`, `/open/models/list` | `{"list":[…]}` |
 *
 * A reader that only looked for `data.list` would turn the first two into an
 * **empty list, silently** — `data` is not an object, so the cast yields `null`
 * and an `orEmpty()` swallows it. Both are accepted here instead, which is also
 * what keeps a future shape change from breaking the picker with no error.
 *
 * Anything that is neither reads as empty: a shape change degrades to "nothing
 * found" rather than throwing out of a `suspend` function (NFR-1 / AC-9).
 */
private fun JsonObject.items(): List<com.google.gson.JsonElement> = when (val data = get(DATA)) {
    is JsonArray -> data.toList()
    is JsonObject -> (data[LIST_KEY] as? JsonArray)?.toList().orEmpty()
    else -> emptyList()
}

/** Non-null view of an element, skipping anything that is not an object. */
private fun com.google.gson.JsonElement.asObject(): JsonObject? = this as? JsonObject

/** `team/list` row. `created_id` is the creator's uid, not the team's id. */
private fun JsonObject?.toTeam(): ApipostTeam? {
    val id = this?.str(TEAM_ID) ?: return null
    return ApipostTeam(teamId = id, name = this.str(NAME).orEmpty())
}

/** `project/list` row. `team_id` comes back on every row, so no state is needed. */
private fun JsonObject?.toProject(): ApipostProject? {
    val id = this?.str(PROJECT_ID) ?: return null
    return ApipostProject(
        projectId = id,
        teamId = this.str(TEAM_ID).orEmpty(),
        name = this.str(NAME).orEmpty(),
    )
}

private fun JsonObject?.toExistingApi(): ApipostExistingApi? {
    val id = this?.str("target_id") ?: return null
    return ApipostExistingApi(
        targetId = id,
        name = this.str("name").orEmpty(),
        method = this.str("method").orEmpty(),
        path = this.str("url").orEmpty(),
        targetType = this.str("target_type") ?: ApipostNode.TARGET_TYPE_API,
    )
}

private fun JsonObject?.toExistingModel(): ApipostExistingModel? {
    val id = this?.str("model_id") ?: return null
    return ApipostExistingModel(
        modelId = id,
        name = this.str("name").orEmpty(),
        modelType = this.str("model_type") ?: ApipostModel.MODEL_TYPE_MODEL,
    )
}

// endregion
