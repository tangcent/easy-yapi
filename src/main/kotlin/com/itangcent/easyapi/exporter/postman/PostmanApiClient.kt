package com.itangcent.easyapi.exporter.postman

import com.google.gson.JsonObject
import com.itangcent.easyapi.exporter.postman.model.PostmanCollection
import com.itangcent.easyapi.exporter.postman.model.PostmanEnvironmentDetail
import com.itangcent.easyapi.exporter.postman.model.PostmanEnvironmentInfo
import com.itangcent.easyapi.exporter.postman.model.PostmanEnvironmentValue
import com.itangcent.easyapi.exporter.postman.model.PostmanEnvironmentCreateRequest
import com.itangcent.easyapi.exporter.postman.model.PostmanEnvironmentUpdateRequest
import com.itangcent.easyapi.exporter.postman.model.PostmanGson
import com.itangcent.easyapi.http.HttpClient
import com.itangcent.easyapi.http.HttpRequest
import com.itangcent.easyapi.http.KeyValue
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.script.env.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Client for interacting with the Postman API.
 *
 * Provides methods to:
 * - Upload and update collections
 * - List workspaces and collections
 *
 * ## API Reference
 * Uses the Postman API v1:
 * - Base URL: https://api.getpostman.com
 * - Authentication: X-Api-Key header
 *
 * @see CachedPostmanApiClient for cached version
 * @see PostmanChannel for usage in export workflow
 */
class PostmanApiClient(
    private val apiKey: String = "",
    private val workspaceId: String? = null,
    private val httpClient: HttpClient
) {
    private val gson = PostmanGson.compact

    suspend fun uploadCollection(collection: PostmanCollection): UploadResult {
        if (apiKey.isBlank()) {
            return UploadResult(success = true, message = "Mock mode - no API key configured")
        }

        return withContext(Dispatchers.IO) {
            try {
                val url = buildUploadUrl()
                val body = gson.toJson(mapOf("collection" to collection))

                val request = HttpRequest(
                    url = url,
                    method = "POST",
                    headers = listOf(
                        "Content-Type" to "application/json",
                        "X-Api-Key" to apiKey
                    ),
                    body = body
                )

                val response = httpClient.execute(request)

                if (response.code == 200) {
                    val result = gson.fromJson(response.body, JsonObject::class.java)
                    val success = result.has("collection")
                    UploadResult(
                        success = success,
                        message = if (success) "Collection uploaded successfully" else "Upload failed",
                        collectionId = result.getAsJsonObject("collection")?.get("uid")?.asString
                    )
                } else {
                    LOG.warn("Postman uploadCollection: HTTP ${response.code}, body=${response.body?.take(500)}")
                    UploadResult(success = false, message = "HTTP ${response.code}")
                }
            } catch (e: Exception) {
                LOG.warn("Postman uploadCollection: exception", e)
                UploadResult(success = false, message = e.message)
            }
        }
    }

    suspend fun updateCollection(collectionUid: String, collection: PostmanCollection): UploadResult {
        if (apiKey.isBlank()) {
            return UploadResult(success = true, message = "Mock mode - no API key configured")
        }

        return withContext(Dispatchers.IO) {
            try {
                val url = "${API_BASE_URL}/collections/$collectionUid"
                val body = gson.toJson(mapOf("collection" to collection))

                val request = HttpRequest(
                    url = url,
                    method = "PUT",
                    headers = listOf(
                        KeyValue("Content-Type", "application/json"),
                        KeyValue("X-Api-Key", apiKey)
                    ),
                    body = body
                )

                val response = httpClient.execute(request)

                if (response.code == 200) {
                    UploadResult(success = true, message = "Collection updated successfully")
                } else {
                    LOG.warn("Postman updateCollection: HTTP ${response.code}, body=${response.body?.take(500)}")
                    UploadResult(success = false, message = "HTTP ${response.code}")
                }
            } catch (e: Exception) {
                LOG.warn("Postman updateCollection: exception", e)
                UploadResult(success = false, message = e.message)
            }
        }
    }

    suspend fun listWorkspaces(): List<Workspace> {
        LOG.info("listWorkspaces called, apiKey.length=${apiKey.length}, apiKey.isBlank=${apiKey.isBlank()}")
        if (apiKey.isBlank()) {
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            val url = "${API_BASE_URL}/workspaces"
            LOG.info("listWorkspaces: requesting $url")

            val request = HttpRequest(
                url = url,
                method = "GET",
                headers = listOf(KeyValue("X-Api-Key", apiKey))
            )

            val response = httpClient.execute(request)
            LOG.info(
                "listWorkspaces: response code=${response.code}, bodyLength=${response.body?.length}, body=${
                    response.body?.take(
                        1000
                    )
                }"
            )

            if (response.code == 200) {
                try {
                    val result = gson.fromJson(response.body, JsonObject::class.java)
                    val arr = result.getAsJsonArray("workspaces")
                    LOG.info("listWorkspaces: parsed workspaces array size=${arr?.size()}")
                    arr?.map { element ->
                        val obj = element.asJsonObject
                        Workspace(
                            id = obj.get("id").asString,
                            name = obj.get("name").asString
                        )
                    } ?: emptyList()
                } catch (e: Exception) {
                    LOG.warn("listWorkspaces: failed to parse response", e)
                    emptyList()
                }
            } else {
                LOG.warn("listWorkspaces: HTTP ${response.code}, body=${response.body?.take(500)}")
                emptyList()
            }
        }
    }

    suspend fun listCollections(workspaceId: String): List<PostmanCollectionInfo> {
        LOG.info("listCollections called, workspaceId=$workspaceId")
        if (apiKey.isBlank()) {
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            val url = "${API_BASE_URL}/collections?workspace=$workspaceId"
            LOG.info("listCollections: requesting $url")

            val request = HttpRequest(
                url = url,
                method = "GET",
                headers = listOf(KeyValue("X-Api-Key", apiKey))
            )

            val response = httpClient.execute(request)

            if (response.code == 200) {
                try {
                    val result = gson.fromJson(response.body, JsonObject::class.java)
                    val arr = result.getAsJsonArray("collections")
                    LOG.info("listCollections: parsed collections array size=${arr?.size()}")
                    arr?.map { element ->
                        val obj = element.asJsonObject
                        PostmanCollectionInfo(
                            id = obj.get("id").asString,
                            name = obj.get("name").asString,
                            uid = obj.get("uid")?.asString
                        )
                    } ?: emptyList()
                } catch (e: Exception) {
                    LOG.warn("listCollections: failed to parse response", e)
                    emptyList()
                }
            } else {
                LOG.warn("listCollections: HTTP ${response.code}")
                emptyList()
            }
        }
    }

    // --- Environment API Methods ---

    suspend fun listEnvironments(workspaceId: String): List<PostmanEnvironmentInfo> {
        LOG.info("listEnvironments called, workspaceId=$workspaceId")
        if (apiKey.isBlank()) {
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            val url = "${API_BASE_URL}/environments?workspace=$workspaceId"
            LOG.info("listEnvironments: requesting $url")

            val request = HttpRequest(
                url = url,
                method = "GET",
                headers = listOf(KeyValue("X-Api-Key", apiKey))
            )

            val response = httpClient.execute(request)

            if (response.code == 200) {
                try {
                    val result = gson.fromJson(response.body, JsonObject::class.java)
                    val arr = result.getAsJsonArray("environments")
                    LOG.info("listEnvironments: parsed environments array size=${arr?.size()}")
                    arr?.map { element ->
                        val obj = element.asJsonObject
                        PostmanEnvironmentInfo(
                            id = obj.get("id").asString,
                            name = obj.get("name").asString,
                            uid = obj.get("uid")?.asString
                        )
                    } ?: emptyList()
                } catch (e: Exception) {
                    LOG.warn("listEnvironments: failed to parse response", e)
                    emptyList()
                }
            } else {
                LOG.warn("listEnvironments: HTTP ${response.code}")
                emptyList()
            }
        }
    }

    suspend fun getEnvironment(environmentId: String): PostmanEnvironmentDetail? {
        LOG.info("getEnvironment called, environmentId=$environmentId")
        if (apiKey.isBlank()) {
            return null
        }

        return withContext(Dispatchers.IO) {
            val url = "${API_BASE_URL}/environments/$environmentId"
            LOG.info("getEnvironment: requesting $url")

            val request = HttpRequest(
                url = url,
                method = "GET",
                headers = listOf(KeyValue("X-Api-Key", apiKey))
            )

            val response = httpClient.execute(request)

            if (response.code == 200) {
                try {
                    val result = gson.fromJson(response.body, JsonObject::class.java)
                    val envObj = result.getAsJsonObject("environment")
                    envObj?.let {
                        val valuesArr = it.getAsJsonArray("values")
                        val values = valuesArr?.map { element ->
                            val v = element.asJsonObject
                            PostmanEnvironmentValue(
                                key = v.get("key").asString,
                                value = v.get("value")?.asString ?: "",
                                enabled = v.get("enabled")?.asBoolean ?: true,
                                type = v.get("type")?.asString ?: "text"
                            )
                        } ?: emptyList()
                        PostmanEnvironmentDetail(
                            id = it.get("id")?.asString ?: "",
                            name = it.get("name").asString,
                            uid = it.get("uid")?.asString,
                            values = values
                        )
                    }
                } catch (e: Exception) {
                    LOG.warn("getEnvironment: failed to parse response", e)
                    null
                }
            } else {
                LOG.warn("getEnvironment: HTTP ${response.code}")
                null
            }
        }
    }

    suspend fun createEnvironment(workspaceId: String, environment: PostmanEnvironmentDetail): UploadResult {
        if (apiKey.isBlank()) {
            return UploadResult(success = true, message = "Mock mode - no API key configured")
        }

        return withContext(Dispatchers.IO) {
            try {
                val url = "${API_BASE_URL}/environments?workspace=$workspaceId"
                val body = gson.toJson(PostmanEnvironmentCreateRequest(environment))

                val request = HttpRequest(
                    url = url,
                    method = "POST",
                    headers = listOf(
                        KeyValue("Content-Type", "application/json"),
                        KeyValue("X-Api-Key", apiKey)
                    ),
                    body = body
                )

                val response = httpClient.execute(request)

                if (response.code == 200) {
                    UploadResult(success = true, message = "Environment created successfully")
                } else {
                    UploadResult(success = false, message = "HTTP ${response.code}: ${response.body?.take(200)}")
                }
            } catch (e: Exception) {
                UploadResult(success = false, message = e.message)
            }
        }
    }

    suspend fun updateEnvironment(environmentId: String, environment: PostmanEnvironmentDetail): UploadResult {
        if (apiKey.isBlank()) {
            return UploadResult(success = true, message = "Mock mode - no API key configured")
        }

        return withContext(Dispatchers.IO) {
            try {
                val url = "${API_BASE_URL}/environments/$environmentId"
                val body = gson.toJson(PostmanEnvironmentUpdateRequest(environment))

                val request = HttpRequest(
                    url = url,
                    method = "PUT",
                    headers = listOf(
                        KeyValue("Content-Type", "application/json"),
                        KeyValue("X-Api-Key", apiKey)
                    ),
                    body = body
                )

                val response = httpClient.execute(request)

                if (response.code == 200) {
                    UploadResult(success = true, message = "Environment updated successfully")
                } else {
                    UploadResult(success = false, message = "HTTP ${response.code}: ${response.body?.take(200)}")
                }
            } catch (e: Exception) {
                UploadResult(success = false, message = e.message)
            }
        }
    }

    private fun buildUploadUrl(): String {
        return if (workspaceId != null) {
            "${API_BASE_URL}/collections?workspace=$workspaceId"
        } else {
            "${API_BASE_URL}/collections"
        }
    }

    companion object : IdeaLog {
        internal const val API_BASE_URL = "https://api.getpostman.com"

        /**
         * Maps an EasyAPI [Environment] to a [PostmanEnvironmentDetail] for upload.
         *
         * Each variable is mapped to a [PostmanEnvironmentValue] with:
         * - `enabled = true`
         * - `type = "text"`
         *
         * Spec: tasks/2.5
         *
         * @param env The EasyAPI environment to map
         * @return Postman environment detail ready for create/update API calls
         */
        fun environmentToVariables(env: Environment): List<PostmanEnvironmentValue> {
            return variablesToPostmanValues(env.variables)
        }

        /**
         * Maps a variable map to a list of [PostmanEnvironmentValue] entries.
         *
         * Each variable is mapped with `enabled = true` and `type = "text"`.
         *
         * Spec: tasks/2.5
         *
         * @param variables The key-value variables to map
         * @return Postman environment values ready for create/update API calls
         */
        fun variablesToPostmanValues(variables: Map<String, String>): List<PostmanEnvironmentValue> {
            return variables.map { (key, value) ->
                PostmanEnvironmentValue(key = key, value = value, enabled = true, type = "text")
            }
        }

        /**
         * Maps Postman environment values to a simple key-value map.
         *
         * Spec: tasks/2.6
         *
         * @param values Postman variable list
         * @param includeDisabled Whether to include disabled variables
         * @return Map of variable key → value
         */
        fun postmanVariablesToMap(
            values: List<PostmanEnvironmentValue>,
            includeDisabled: Boolean
        ): Map<String, String> {
            return values
                .filter { includeDisabled || it.enabled }
                .associate { it.key to it.value }
        }
    }
}

data class UploadResult(
    val success: Boolean,
    val message: String? = null,
    val collectionId: String? = null
)

data class Workspace(
    val id: String,
    val name: String
)

data class PostmanCollectionInfo(
    val id: String,
    val name: String,
    val uid: String? = null
)