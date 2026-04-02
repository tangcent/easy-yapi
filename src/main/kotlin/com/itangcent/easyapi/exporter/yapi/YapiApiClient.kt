package com.itangcent.easyapi.exporter.yapi

import com.itangcent.easyapi.exporter.yapi.model.TokenValidationResult
import com.itangcent.easyapi.exporter.yapi.model.YapiApiDoc
import com.itangcent.easyapi.exporter.yapi.model.YapiCart
import com.itangcent.easyapi.exporter.yapi.model.YapiSaveResult
import com.itangcent.easyapi.http.HttpClient
import com.itangcent.easyapi.http.HttpRequest
import com.itangcent.easyapi.http.KeyValue
import com.itangcent.easyapi.util.GsonUtils
import com.google.gson.JsonObject
import com.itangcent.easyapi.logging.IdeaLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * API client for interacting with YAPI server.
 * 
 * This client provides methods for:
 * - Project information retrieval
 * - Token validation
 * - Category (cart) management
 * - API documentation upload
 * 
 * All API calls are made asynchronously using coroutines.
 * 
 * @param serverUrl The base URL of the YAPI server
 * @param token The project token for authentication
 * @param httpClient The HTTP client for making requests
 */
class YapiApiClient(
    private val serverUrl: String = "",
    private val token: String = "",
    private val httpClient: HttpClient
) {
    /** Base URL without trailing slash */
    private val baseUrl get() = serverUrl.trimEnd('/')

    /** Cached project ID resolved from the token */
    private var cachedProjectId: String? = null

    /** API endpoint paths */
    companion object : IdeaLog {
        private const val GET_PROJECT = "/api/project/get"
        private const val SAVE_API = "/api/interface/save"
        private const val GET_CAT_MENU = "/api/interface/getCatMenu"
        private const val ADD_CAT = "/api/interface/add_cat"
    }

    // region Project

    /**
     * Resolves the YAPI project ID from the token.
     * Caches the result for subsequent calls.
     * 
     * @return The project ID, or null if resolution fails
     */
    suspend fun getProjectId(): String? {
        cachedProjectId?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl$GET_PROJECT?token=${enc(token)}"
                val resp = httpClient.execute(HttpRequest(url = url, method = "GET"))
                if (resp.code == 200) {
                    val json = GsonUtils.fromJson<JsonObject>(resp.body ?: "")
                    if (json.get("errcode")?.asInt == 0) {
                        val id = json.getAsJsonObject("data")?.get("_id")?.asString
                        cachedProjectId = id
                        id
                    } else null
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Validates the token by attempting to resolve the project ID.
     * Returns detailed validation result with error reasons.
     *
     * @return Valid result with project ID, or Failed result with reason
     */
    suspend fun validateToken(): TokenValidationResult {
        if (serverUrl.isBlank()) {
            return TokenValidationResult.Failed("YAPI server URL is not configured")
        }
        if (token.isBlank()) {
            return TokenValidationResult.Failed("Token is empty")
        }
        return withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl$GET_PROJECT?token=${enc(token)}"
                val resp = httpClient.execute(HttpRequest(url = url, method = "GET"))
                if (resp.code == 200) {
                    val json = GsonUtils.fromJson<JsonObject>(resp.body ?: "")
                    val errCode = json.get("errcode")?.asInt
                    if (errCode == 0) {
                        val id = json.getAsJsonObject("data")?.get("_id")?.asString
                        if (id != null) {
                            cachedProjectId = id
                            TokenValidationResult.Valid(id)
                        } else {
                            TokenValidationResult.Failed("Could not resolve project ID from response. URL: $url")
                        }
                    } else {
                        val errMsg = json.get("errmsg")?.asString ?: "Unknown error"
                        TokenValidationResult.Failed("Token may be invalid: $errMsg (errcode: $errCode). URL: $url")
                    }
                } else {
                    TokenValidationResult.Failed("YAPI server returned HTTP ${resp.code}. URL: $url")
                }
            } catch (e: java.net.SocketTimeoutException) {
                val url = "$baseUrl$GET_PROJECT?token=${enc(token)}"
                TokenValidationResult.Failed("Request timeout - YAPI server took too long to respond. Try increasing timeout in Settings > EasyApi > Http. URL: $url")
            } catch (e: java.net.ConnectException) {
                val url = "$baseUrl$GET_PROJECT?token=${enc(token)}"
                TokenValidationResult.Failed("Cannot connect to YAPI server: ${e.message}. URL: $url")
            } catch (e: java.net.UnknownHostException) {
                val url = "$baseUrl$GET_PROJECT?token=${enc(token)}"
                TokenValidationResult.Failed("Cannot resolve YAPI server host: ${e.message}. URL: $url")
            } catch (e: Exception) {
                val url = "$baseUrl$GET_PROJECT?token=${enc(token)}"
                TokenValidationResult.Failed("Failed to validate token: ${e.message}. URL: $url")
            }
        }
    }

    // endregion

    // region Carts (categories)

    /**
     * Lists all categories (carts) in the project.
     * 
     * @return List of YAPI carts, empty list if none or on error
     */
    suspend fun listCarts(): List<YapiCart> {
        val projectId = getProjectId() ?: return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl$GET_CAT_MENU?project_id=${enc(projectId)}&token=${enc(token)}"
                val resp = httpClient.execute(HttpRequest(url = url, method = "GET"))
                if (resp.code == 200) {
                    val json = GsonUtils.fromJson<JsonObject>(resp.body ?: "")
                    if (json.get("errcode")?.asInt == 0) {
                        return@withContext json.getAsJsonArray("data")?.map { element ->
                            val obj = element.asJsonObject
                            YapiCart(
                                id = obj.get("_id").asLong,
                                name = obj.get("name").asString
                            )
                        } ?: emptyList()
                    }
                }
            } catch (_: Exception) {
                null
            }
            emptyList()
        }
    }

    /**
     * Creates a new category (cart) in the project.
     * 
     * @param name The cart name
     * @param desc Optional cart description
     * @return The created cart, or null on failure
     */
    suspend fun createCart(name: String, desc: String = ""): YapiCart? {
        val projectId = getProjectId() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl$ADD_CAT"
                val body = GsonUtils.toJson(
                    linkedMapOf(
                        "desc" to desc,
                        "project_id" to projectId,
                        "name" to name,
                        "token" to token
                    )
                )
                val resp = httpClient.execute(
                    HttpRequest(
                        url = url,
                        method = "POST",
                        headers = listOf(KeyValue("Content-Type", "application/json")),
                        body = body
                    )
                )
                if (resp.code == 200) {
                    val json = GsonUtils.fromJson<JsonObject>(resp.body ?: "")
                    if (json.get("errcode")?.asInt == 0) {
                        val data = json.getAsJsonObject("data")
                        YapiCart(
                            id = data.get("_id").asLong,
                            name = data.get("name").asString
                        )
                    } else null
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Finds an existing cart by name, or creates one if it doesn't exist.
     * 
     * @param name The cart name to find or create
     * @param desc Optional description for new cart
     * @return The cart ID, or null on failure
     */
    suspend fun findOrCreateCart(name: String, desc: String = ""): String? {
        val carts = listCarts()
        val existing = carts.firstOrNull { it.name == name }
        if (existing != null) return existing.id.toString()

        val created = createCart(name, desc)
        return created?.id?.toString()
    }

    // endregion

    // endregion

    // region API upload

    /**
     * Uploads an API document to YAPI.
     * 
     * @param doc The YAPI document to upload
     * @param catId The category ID to place the API in
     * @return Save result indicating success or failure
     */
    suspend fun uploadApi(doc: YapiApiDoc, catId: String): YapiSaveResult {
        if (serverUrl.isBlank() || token.isBlank()) {
            return YapiSaveResult(success = true, message = "Mock mode - no server configured")
        }
        return withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl$SAVE_API"
                val body = buildApiDocBody(doc, catId)
                val resp = httpClient.execute(
                    HttpRequest(
                        url = url,
                        method = "POST",
                        headers = listOf("Content-Type" to "application/json"),
                        body = body
                    )
                )
                if (resp.code == 200) {
                    val json = GsonUtils.fromJson<JsonObject>(resp.body ?: "")
                    val success = json.get("errcode")?.asInt == 0
                    YapiSaveResult(success = success, message = json.get("errmsg")?.asString)
                } else {
                    YapiSaveResult(success = false, message = "HTTP ${resp.code}")
                }
            } catch (e: Exception) {
                YapiSaveResult(success = false, message = e.message)
            }
        }
    }

    // endregion

    /**
     * Builds the JSON body for API document upload.
     * Transforms YapiApiDoc into the format expected by YAPI API.
     * 
     * @param doc The API document
     * @param catId The category ID
     * @return JSON string for the request body
     */
    private fun buildApiDocBody(doc: YapiApiDoc, catId: String): String {
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
                    "name" to it.name,
                    "value" to (it.value ?: ""),
                    "desc" to (it.desc ?: ""),
                    "example" to (it.example ?: it.value ?: ""),
                    "required" to it.required
                )
            } ?: emptyList<Any>()),
            "req_query" to (doc.reqQuery?.map {
                linkedMapOf(
                    "name" to it.name,
                    "value" to (it.value ?: ""),
                    "example" to (it.example ?: it.value ?: ""),
                    "desc" to (it.desc ?: ""),
                    "required" to it.required
                )
            } ?: emptyList<Any>()),
            "req_params" to (doc.reqParams?.map {
                linkedMapOf(
                    "name" to it.name,
                    "example" to (it.example ?: ""),
                    "desc" to (it.desc ?: "")
                )
            } ?: emptyList<Any>()),
            "req_body_other" to (doc.reqBodyOther ?: ""),
            "res_body" to (doc.resBody ?: ""),
            "tags" to (doc.tags ?: emptyList<String>())
        )

        doc.reqBodyType?.let { map["req_body_type"] = it }
        doc.resBodyType?.let { map["res_body_type"] = it }

        if (doc.reqBodyForm != null) {
            map["req_body_form"] = doc.reqBodyForm.map {
                linkedMapOf(
                    "name" to it.name,
                    "example" to (it.example ?: ""),
                    "type" to it.type,
                    "required" to it.required,
                    "desc" to (it.desc ?: "")
                )
            }
        } else {
            map["req_body_form"] = emptyList<Any>()
        }

        return GsonUtils.toJson(map)
    }

    /** URL-encodes a string value. */
    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
