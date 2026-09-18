package com.itangcent.easyapi.channel.apipost

import com.intellij.openapi.project.Project

/**
 * Result of a single ApiPost open-API call.
 *
 * Sealed instead of exception-throwing: every failure a push can hit — bad
 * token, missing route, network error — is a value here, so [ApipostExporter]
 * never needs a `try/catch` and no exception can escape into the orchestrator
 * (NFR-1 / AC-9).
 *
 * ## Success is decided by the body, not by HTTP status
 *
 * Probed live on 2026-09-16 (see `REVIEW.md §6.8`):
 *
 * - an existing route with a bad token answers **`HTTP 200`** +
 *   `{"code":30001,"msg":"api_token错误或已被删除！"}`
 * - a missing header answers `HTTP 200` + `{"code":30002,"msg":"api-token必填"}`
 * - only a **non-existent** route answers `HTTP 404`
 *
 * So `HTTP 200` says "the route is there", not "the call worked". [Failure.httpCode]
 * is carried for diagnostics only; [Failure.code] is the business code that
 * explains what went wrong.
 */
sealed class ApipostSyncResult<out T> {

    data class Success<T>(val data: T) : ApipostSyncResult<T>()

    data class Failure(
        val message: String,
        /** Business code from the response body (`30001`, `30002`, …). */
        val code: Int? = null,
        /** Transport status. Non-200 is rare — see the KDoc on this class. */
        val httpCode: Int? = null,
    ) : ApipostSyncResult<Nothing>()

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.data

    fun errorMessage(): String? = (this as? Failure)?.message
}

/**
 * Business codes returned in the response body by ApiPost's open API.
 *
 * Probed live on 2026-09-17 against a real `api-token` (`REVIEW.md §6.9.6`).
 * These live in the **body**, never in the HTTP status: an existing route answers
 * `HTTP 200` even when the call failed, and `404` only means the route itself is
 * absent (`REVIEW.md §6.8.2`).
 *
 * [API_FIELD_REQUIRED] and [MODEL_FIELD_REQUIRED] are deliberately distinct —
 * the models endpoints answer with their own code, which is how a failure can be
 * attributed to the right side of the push.
 */
object ApipostCodes {

    /** Success. The only value that means the call worked. */
    const val OK = 0

    /** A field is missing on an apis-endpoint payload; `msg` lists the offenders. */
    const val API_FIELD_REQUIRED = 10001

    /** A field is missing on a models-endpoint payload. */
    const val MODEL_FIELD_REQUIRED = 10002

    /**
     * `12000 该团队不存在或已被团队管理员解散` — `team_id` does not resolve, or
     * the team is out of the token's reach. Verified live on 2026-09-17.
     */
    const val TEAM_NOT_FOUND = 12000

    /** `project_id` does not resolve to a project the token can see. */
    const val PROJECT_NOT_FOUND = 13000

    /**
     * `14000 接口已存在` — an API or folder with that name/method+url is already
     * there. Treated as a **signal**, not a failure: the dedup index built from
     * [ApipostApiClient.listApis] should have caught it first, and a race that
     * slips through lands here.
     */
    const val API_ALREADY_EXISTS = 14000

    /** `14002 父节点已删除` — `parent_id` points at a folder that does not exist. */
    const val API_PARENT_MISSING = 14002

    /** `16002 模型名称已存在` — the models-side counterpart of [API_ALREADY_EXISTS]. */
    const val MODEL_NAME_EXISTS = 16002

    /**
     * `16005 父级模型目录不存在` — the models endpoints reject any non-root
     * `parent_id`, so a model can never be nested through the open API even
     * though a folder can be created (`REVIEW.md §6.9.4`).
     */
    const val MODEL_PARENT_MISSING = 16005

    /** Token is wrong or deleted. */
    const val TOKEN_INVALID = 30001

    /** `api-token` header is absent. */
    const val TOKEN_REQUIRED = 30002
}

/**
 * An API that already exists in the ApiPost project, as returned by
 * `GET /open/apis/list`.
 *
 * [targetId] is the server-side id [ApipostApiClient.updateApi] must carry for
 * an in-place update instead of a duplicate create (AC-6).
 *
 * [name] matters for **folders**: a folder that already exists server-side is
 * reused by name rather than re-created (`14000 接口已存在` otherwise), and its
 * server id is what the child APIs' `parent_id` must point at
 * (`REVIEW.md §6.9.3`).
 *
 * [path] is ApiPost's own `url` field renamed for readability at this boundary —
 * the wire format calls it `url`.
 */
data class ApipostExistingApi(
    val targetId: String,
    val name: String,
    val method: String,
    val path: String,
    val targetType: String = ApipostNode.TARGET_TYPE_API,
)

/**
 * A team the token can see, as returned by `GET /open/team/list`.
 *
 * That route takes **no** parameters and answers every team of the token's user,
 * which is what makes it the entry point for picking a project
 * (`REVIEW.md §6.11.1`).
 */
data class ApipostTeam(
    val teamId: String,
    val name: String,
)

/**
 * A project inside one team, as returned by `GET /open/project/list?team_id=`.
 *
 * [teamId] is echoed back by the server on every row, so a project can be traced
 * to its team without keeping the request parameter around.
 */
data class ApipostProject(
    val projectId: String,
    val teamId: String,
    val name: String,
)

/**
 * A data model that already exists in the ApiPost project, as returned by
 * `GET /open/models/list`.
 *
 * That route was confirmed to exist on 2026-09-16 (OQ-2), which is why models
 * are deduped by [name] the same way APIs are deduped by method + path.
 */
data class ApipostExistingModel(
    val modelId: String,
    val name: String,
    val modelType: String = ApipostModel.MODEL_TYPE_MODEL,
)

/**
 * Client for the ApiPost open API, scoped to one project id.
 *
 * Every route here was confirmed to exist by live probing (`REVIEW.md §6.8.3`),
 * and every required field was pinned by error-driven probing against a real
 * `api-token` (`REVIEW.md §6.9.1`). Two wire facts shape this interface:
 *
 * 1. **Folders ride the API route.** There is no separate folder endpoint —
 *    `POST /open/apis/create` with `target_type = "folder"` creates one, and a
 *    child API's `parent_id` is honoured, so the hierarchy survives a push
 *    (`REVIEW.md §6.9.3`, which reverses the earlier OQ-5 conclusion).
 * 2. **`createApi` must hand back the server-side id.** The server honours a
 *    client-supplied `target_id` but **regenerates** `model_id`, so anything
 *    that has to be referenced later (a `$ref`, a child's `parent_id`) can only
 *    be wired up from what the server returns (`REVIEW.md §6.9.5`).
 *
 * All methods return [ApipostSyncResult] — none of them throw.
 */
interface ApipostApiClient {

    /**
     * `GET /open/team/list` — the teams the token can see. Takes no parameters.
     *
     * The first of a **two-hop** lookup: projects can only be listed per team,
     * so a picker has to go teams → projects (`REVIEW.md §6.11.1`).
     */
    suspend fun listTeams(): ApipostSyncResult<List<ApipostTeam>>

    /**
     * `GET /open/project/list` — one team's projects.
     *
     * `team_id` is required; there is no route that lists every project a token
     * can reach in one call, and `/open/projects/list` answers `404`.
     */
    suspend fun listProjects(teamId: String): ApipostSyncResult<List<ApipostProject>>

    /** `GET /open/apis/list` — the dedup index for the whole push, folders included. */
    suspend fun listApis(projectId: String): ApipostSyncResult<List<ApipostExistingApi>>

    /**
     * `POST /open/apis/create` — pushes one new API **or folder**.
     *
     * @return the server-side `target_id` of the created node. For a folder this
     *         is what the children's `parent_id` must reference.
     */
    suspend fun createApi(projectId: String, node: ApipostNode): ApipostSyncResult<String>

    /**
     * `POST /open/apis/update` — updates one existing API in place.
     *
     * Unlike [createApi] the wire contract **requires** `method` and `name` on
     * update (`REVIEW.md §6.9.1`), so the implementation must not send a
     * stripped-down payload here.
     */
    suspend fun updateApi(projectId: String, targetId: String, node: ApipostNode): ApipostSyncResult<Unit>

    /** `GET /open/models/list` — the dedup index for data models. */
    suspend fun listModels(projectId: String): ApipostSyncResult<List<ApipostExistingModel>>

    /**
     * `POST /open/models/create` — pushes one new data model.
     *
     * @return the server-side `model_id`. The server ignores a client-supplied
     *         one, so this is the only trustworthy source for `$ref` rewriting.
     */
    suspend fun createModel(projectId: String, model: ApipostModel): ApipostSyncResult<String>

    /**
     * `POST /open/models/update` — updates one existing data model in place.
     *
     * The wire contract needs `model_id` **and** `original_name` here
     * (`REVIEW.md §6.9.1`), which is why [modelId] is a separate parameter
     * rather than being read off [model].
     *
     * @return the server-side `model_id` (unchanged, but returned so callers can
     *         build the `$ref` map without special-casing the update path).
     */
    suspend fun updateModel(projectId: String, modelId: String, model: ApipostModel): ApipostSyncResult<String>
}

/**
 * Builds an [ApipostApiClient] for one export.
 *
 * A factory rather than a direct constructor call so that [ApipostChannel] can
 * be instantiated by the extension point (no-arg constructor) while tests
 * still substitute a mock client.
 */
fun interface ApipostApiClientFactory {
    fun create(baseUrl: String, token: String, project: Project): ApipostApiClient
}
