package com.itangcent.easyapi.exporter.postman

import com.google.gson.JsonObject
import com.itangcent.easyapi.exporter.postman.model.PostmanCollection
import com.itangcent.easyapi.exporter.postman.model.postmanGson
import com.itangcent.easyapi.http.HttpClient
import com.itangcent.easyapi.http.HttpRequest
import com.itangcent.easyapi.http.KeyValue
import com.itangcent.easyapi.logging.IdeaLog
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
 * @see PostmanExporter for usage in export workflow
 */
class PostmanApiClient(
    private val apiKey: String = "",
    private val workspaceId: String? = null,
    private val httpClient: HttpClient
) {
    private val gson = postmanGson(prettyPrint = false)

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
                    UploadResult(success = false, message = "HTTP ${response.code}")
                }
            } catch (e: Exception) {
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
                    UploadResult(success = false, message = "HTTP ${response.code}")
                }
            } catch (e: Exception) {
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

    private fun buildUploadUrl(): String {
        return if (workspaceId != null) {
            "${API_BASE_URL}/collections?workspace=$workspaceId"
        } else {
            "${API_BASE_URL}/collections"
        }
    }

    companion object : IdeaLog {
        internal const val API_BASE_URL = "https://api.getpostman.com"
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