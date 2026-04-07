package com.itangcent.easyapi.exporter.yapi

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.itangcent.easyapi.exporter.yapi.model.YapiApiDoc
import com.itangcent.easyapi.exporter.yapi.model.YapiCart
import com.itangcent.easyapi.exporter.yapi.model.YapiResponse
import com.itangcent.easyapi.http.HttpClient
import com.itangcent.easyapi.http.HttpRequest
import com.itangcent.easyapi.http.HttpResponse
import com.itangcent.easyapi.http.KeyValue
import com.itangcent.easyapi.util.GsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * Default implementation of [YapiApiClient] that communicates with a real YAPI server.
 *
 * Project ID resolution strategy (in order):
 * 1. Instance-level [cachedProjectId] — avoids repeated network calls within the same client lifetime
 * 2. [GET_PROJECT] (`/api/project/get`) — the standard token-to-project endpoint
 * 3. [LIST_MENU] (`/api/interface/list_menu`) — fallback for servers that return no project data
 *    from GET_PROJECT; each menu item carries a `project_id` field
 *
 * Deduplication: [uploadApi] calls [findExistingApi] before saving. If an API with the same
 * path+method already exists in the target category, its `_id` is injected into the save payload
 * so YAPI updates the existing entry instead of creating a duplicate.
 *
 * @param serverUrl Base URL of the YAPI server (trailing slash is trimmed automatically)
 * @param token Project private token used for authentication
 * @param httpClient HTTP client used for all network calls
 */
class DefaultYapiApiClient(
    private val serverUrl: String,
    private val token: String,
    private val httpClient: HttpClient
) : YapiApiClient {

    /** Cached project ID for this client instance. Populated on first [getProjectId] call. */
    private var cachedProjectId: String? = null

    // region Project

    /**
     * Resolves the YAPI project ID for this client's token.
     * Result is cached on the instance — subsequent calls return immediately.
     */
    override suspend fun getProjectId(): YapiResponse<String> {
        cachedProjectId?.let { return YapiResponse.success(it) }

        if (serverUrl.isBlank()) return YapiResponse.failure("YAPI server URL is not configured")
        if (token.isBlank()) return YapiResponse.failure("Token is empty")

        return withContext(Dispatchers.IO) {
            val fromProject = resolveProjectIdFromGetProject()
            if (fromProject != null) {
                cachedProjectId = fromProject
                return@withContext YapiResponse.success(fromProject)
            }

            val fromMenu = resolveProjectIdFromListMenu()
            if (fromMenu != null) {
                cachedProjectId = fromMenu
                return@withContext YapiResponse.success(fromMenu)
            }

            YapiResponse.failure("Could not resolve project ID from token")
        }
    }

    /** Tries to extract the project ID from the standard [GET_PROJECT] endpoint. */
    private suspend fun resolveProjectIdFromGetProject(): String? = runCatching {
        val resp = httpClient.execute(HttpRequest(url = "$serverUrl$GET_PROJECT?token=${enc(token)}", method = "GET"))
        parseResponse(resp) { it.getAsJsonObject("data")?.get("_id")?.asString }.getOrNull()
    }.getOrNull()

    /**
     * Fallback: extracts `project_id` from the first item returned by [LIST_MENU].
     * Used when [GET_PROJECT] returns a success response but no project data.
     */
    private suspend fun resolveProjectIdFromListMenu(): String? = runCatching {
        val resp = httpClient.execute(HttpRequest(url = "$serverUrl$LIST_MENU?token=${enc(token)}", method = "GET"))
        parseResponse(resp) { json ->
            json.getAsJsonArray("data")?.firstOrNull()?.asJsonObject?.get("project_id")?.asString
        }.getOrNull()
    }.getOrNull()

    // endregion

    // region Carts

    /** Lists all categories (carts) in the project via [GET_CAT_MENU]. */
    override suspend fun listCarts(): YapiResponse<List<YapiCart>> {
        val projectId = getProjectId().getOrNull()
            ?: return YapiResponse.failure("Could not resolve project ID")
        return withContext(Dispatchers.IO) {
            runCatching {
                val url = "$serverUrl$GET_CAT_MENU?project_id=${enc(projectId)}&token=${enc(token)}"
                val resp = httpClient.execute(HttpRequest(url = url, method = "GET"))
                parseResponse(resp) { json ->
                    json.getAsJsonArray("data")?.map { element ->
                        val obj = element.asJsonObject
                        YapiCart(id = obj.get("_id").asLong, name = obj.get("name").asString)
                    } ?: emptyList()
                }
            }.getOrElse { YapiResponse.failure(it.message ?: "Unknown error") }
        }
    }

    /** Creates a new category (cart) in the project via [ADD_CAT]. */
    override suspend fun createCart(name: String, desc: String): YapiResponse<YapiCart> {
        val projectId = getProjectId().getOrNull()
            ?: return YapiResponse.failure("Could not resolve project ID")
        return withContext(Dispatchers.IO) {
            runCatching {
                val body = GsonUtils.toJson(
                    linkedMapOf("desc" to desc, "project_id" to projectId, "name" to name, "token" to token)
                )
                val resp = httpClient.execute(
                    HttpRequest(
                        url = "$serverUrl$ADD_CAT",
                        method = "POST",
                        headers = listOf(KeyValue("Content-Type", "application/json")),
                        body = body
                    )
                )
                parseResponse(resp) { json ->
                    val data = json.getAsJsonObject("data")
                    YapiCart(id = data.get("_id").asLong, name = data.get("name").asString)
                }
            }.getOrElse { YapiResponse.failure(it.message ?: "Unknown error") }
        }
    }

    override suspend fun findOrCreateCart(name: String, desc: String): YapiResponse<String> {
        val carts = listCarts().getOrNull() ?: return YapiResponse.failure("Failed to list carts")
        val existing = carts.firstOrNull { it.name == name }
        if (existing != null) return YapiResponse.success(existing.id.toString())
        return createCart(name, desc).let { result ->
            result.getOrNull()?.let { YapiResponse.success(it.id.toString()) }
                ?: YapiResponse.failure(result.errorMessage() ?: "Failed to create cart '$name'")
        }
    }

    // endregion

    // region API listing & deduplication

    /**
     * Lists all APIs in a category using [LIST_CAT].
     * Auto-tunes [apiPageLimit] upward (×1.4) when the response size equals the current limit,
     * then retries — same adaptive strategy as the legacy plugin.
     */
    override suspend fun listApis(catId: String): YapiResponse<JsonArray> = listApis(catId, apiPageLimit)

    private suspend fun listApis(catId: String, limit: Int): YapiResponse<JsonArray> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val url = "$serverUrl$LIST_CAT?token=${enc(token)}&catid=${enc(catId)}&limit=$limit"
                val resp = httpClient.execute(HttpRequest(url = url, method = "GET"))
                val result = parseResponse(resp) { json ->
                    json.getAsJsonObject("data")?.getAsJsonArray("list") ?: JsonArray()
                }
                val list = result.getOrNull() ?: return@runCatching result
                if (list.size() == limit && limit < 5000) {
                    apiPageLimit = (limit * 1.4).toInt()
                    return@runCatching listApis(catId, apiPageLimit)
                }
                result
            }.getOrElse { YapiResponse.failure(it.message ?: "Unknown error") }
        }
    }

    /**
     * Searches [catId] for an existing API matching [path] and [method] (case-insensitive).
     * Returns the YAPI `_id` of the match, or null if not found.
     * Used by [uploadApi] to decide whether to create or update.
     */
    override suspend fun findExistingApi(catId: String, path: String, method: String): String? {
        val apis = listApis(catId).getOrNull() ?: return null
        return apis.firstOrNull { element ->
            val obj = element.asJsonObject
            obj.get("path")?.asString == path &&
                    obj.get("method")?.asString?.equals(method, ignoreCase = true) == true
        }?.asJsonObject?.get("_id")?.asString
    }

    // endregion

    // region API upload

    /**
     * Uploads [doc] to YAPI under [catId].
     * Before saving, calls [findExistingApi] to check for a duplicate by path+method.
     * If found, injects the existing `_id` into the payload so YAPI updates in-place.
     * Returns success with [Unit] on success, or failure with an error message.
     */
    override suspend fun uploadApi(doc: YapiApiDoc, catId: String): YapiResponse<Unit> {
        if (serverUrl.isBlank() || token.isBlank()) return YapiResponse.success(Unit)
        return withContext(Dispatchers.IO) {
            runCatching {
                val existingId = findExistingApi(catId, doc.path, doc.method)
                val resp = httpClient.execute(
                    HttpRequest(
                        url = "$serverUrl$SAVE_API",
                        method = "POST",
                        headers = listOf("Content-Type" to "application/json"),
                        body = buildApiDocBody(doc, catId, existingId)
                    )
                )
                parseResponse(resp) { Unit }
            }.getOrElse { YapiResponse.failure(it.message ?: "Unknown error") }
        }
    }

    // endregion

    /**
     * Parses a raw [HttpResponse] into a typed [YapiResponse].
     *
     * Handles multiple YAPI server response formats:
     * - Standard YAPI: `{"errcode":0, "errmsg":"...", "data":{...}}`
     * - Some forks:    `{"code":0, "message":"成功", "data":{...}}`
     *
     * Success is determined by: `errcode == 0`, or `code == 0`, or `message` being a known
     * success string (e.g. "成功", "成功！") when no code field is present.
     *
     * Failure cases:
     * - HTTP status != 200
     * - Body is not valid JSON
     * - Response code field is non-zero (YAPI application-level error)
     * - [handle] returns null (missing expected data)
     *
     * @param res The raw HTTP response
     * @param handle Extracts the typed result from the parsed JSON body
     */
    private fun <T> parseResponse(res: HttpResponse, handle: (JsonObject) -> T?): YapiResponse<T> {
        if (res.code != 200) return YapiResponse.failure("HTTP ${res.code}")
        val json = runCatching { GsonUtils.fromJson<JsonObject>(res.body ?: "") }.getOrNull()
            ?: return YapiResponse.failure("Invalid JSON response")
        val errcode = json.get("errcode")?.asInt ?: json.get("code")?.asInt
        val errmsg = json.get("errmsg")?.asString ?: json.get("message")?.asString
        val isSuccess = errmsg in SUCCESS_MESSAGES || errcode in SUCCESS_CODES
        if (!isSuccess) {
            return YapiResponse.failure(errmsg ?: "Unknown error (errcode: $errcode)")
        }
        return handle(json)?.let { YapiResponse.success(it) }
            ?: YapiResponse.failure("Empty data in response")
    }

    companion object {
        private const val GET_PROJECT = "/api/project/get"
        private const val LIST_MENU = "/api/interface/list_menu"
        private const val SAVE_API = "/api/interface/save"
        private const val GET_CAT_MENU = "/api/interface/getCatMenu"
        private const val ADD_CAT = "/api/interface/add_cat"
        private const val LIST_CAT = "/api/interface/list_cat"

        /** Known success messages from various YAPI forks. */
        private val SUCCESS_MESSAGES = setOf("成功", "成功！")

        /** Known success codes: 0 (standard YAPI) and 200 (some forks). */
        private val SUCCESS_CODES = setOf(0, 200)

        /**
         * Page size for [LIST_CAT] requests. Auto-tuned upward (×1.4) when a response hits the
         * current limit, capped at 5000. Shared across instances so the tuned value persists for
         * the lifetime of the process.
         */
        @Volatile
        private var apiPageLimit: Int = 1000
    }

    /**
     * Serializes [doc] and [catId] into the JSON body expected by [SAVE_API].
     * If [existingId] is provided it is included as `id`, triggering an update instead of insert.
     */
    private fun buildApiDocBody(doc: YapiApiDoc, catId: String, existingId: String? = null): String {
        val map = mutableMapOf<String, Any?>(
            "token" to token,
            "catid" to catId,
            "title" to doc.title,
            "path" to doc.path,
            "method" to doc.method,
            "desc" to doc.desc,
            "status" to (doc.status ?: "done"),
            "req_body_is_json_schema" to doc.reqBodyIsJsonSchema,
            "res_body_is_json_schema" to doc.resBodyIsJsonSchema,
            "req_headers" to (doc.reqHeaders?.map {
                linkedMapOf(
                    "name" to it.name, "value" to (it.value ?: ""), "desc" to (it.desc ?: ""),
                    "example" to (it.example ?: it.value ?: ""), "required" to it.required
                )
            } ?: emptyList<Any>()),
            "req_query" to (doc.reqQuery?.map {
                linkedMapOf(
                    "name" to it.name, "value" to (it.value ?: ""), "example" to (it.example ?: it.value ?: ""),
                    "desc" to (it.desc ?: ""), "required" to it.required
                )
            } ?: emptyList<Any>()),
            "req_params" to (doc.reqParams?.map {
                linkedMapOf("name" to it.name, "example" to (it.example ?: ""), "desc" to (it.desc ?: ""))
            } ?: emptyList<Any>()),
            "req_body_other" to (doc.reqBodyOther ?: ""),
            "res_body" to (doc.resBody ?: ""),
            "tags" to (doc.tags ?: emptyList<String>()),
            "req_body_form" to (doc.reqBodyForm?.map {
                linkedMapOf(
                    "name" to it.name, "example" to (it.example ?: ""), "type" to it.type,
                    "required" to it.required, "desc" to (it.desc ?: "")
                )
            } ?: emptyList<Any>())
        )
        doc.reqBodyType?.let { map["req_body_type"] = it }
        doc.resBodyType?.let { map["res_body_type"] = it }
        existingId?.let { map["id"] = it }
        return GsonUtils.toJson(map)
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
