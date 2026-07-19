package com.itangcent.easyapi.channel.hoppscotch

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.itangcent.easyapi.channel.hoppscotch.model.HoppCollection
import com.itangcent.easyapi.channel.hoppscotch.model.hoppscotchGson
import com.itangcent.easyapi.core.http.HttpClient
import com.itangcent.easyapi.core.http.HttpRequest
import com.itangcent.easyapi.core.http.KeyValue
import com.itangcent.easyapi.core.logging.IdeaLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * GraphQL API client for Hoppscotch cloud operations.
 *
 * Communicates with Hoppscotch's GraphQL endpoint to manage collections and teams.
 * Uses the project's [HttpClient] interface (not `java.net.http.HttpClient`) for all
 * network requests, and always includes an `Authorization: Bearer <token>` header.
 *
 * Supports custom server URLs for self-hosted Hoppscotch instances via [serverUrl].
 *
 * ## Cloud vs Self-Hosted URL Resolution
 * - **Cloud** (`https://hoppscotch.io`): API calls go to `https://api.hoppscotch.io`
 * - **Self-hosted** (`https://your-instance.com`): API calls go to the same URL
 *
 * This matches the Hoppscotch frontend's `VITE_BACKEND_GQL_URL` default:
 * `https://api.hoppscotch.io/graphql`
 *
 * ## Key operations
 * - [testConnection] — validate the access token
 * - [listTeams] / [listCollections] — discover workspaces and collections
 * - [uploadCollection] — import a collection via JSON
 * - [updateCollection] — replace an existing collection (create-first-then-delete)
 * - [deleteCollection] — remove a collection by ID
 *
 * @property token Hoppscotch access token (Bearer token)
 * @property serverUrl Hoppscotch server base URL (default: https://hoppscotch.io)
 * @property HttpClient the HTTP client used for network requests
 * @see CachedHoppscotchApiClient for a caching decorator
 * @see HoppscotchAuthException thrown on 401 responses
 */
class HoppscotchApiClient(
    private val token: String,
    private val serverUrl: String = DEFAULT_SERVER_URL,
    private val backendUrl: String? = null,
    private val httpClient: HttpClient
) : IdeaLog {

    private val gson = hoppscotchGson(prettyPrint = false)

    private val apiBaseUrl: String
        get() = resolveApiBaseUrl(serverUrl)

    suspend fun testConnection(): Boolean {
        if (token.isBlank()) return false
        return withContext(Dispatchers.IO) {
            try {
                val query = """{ me { uid displayName } }"""
                val response = executeGraphQL(query)
                response != null && !response.has("errors")
            } catch (e: Exception) {
                LOG.warn("Hoppscotch connection test failed", e)
                false
            }
        }
    }

    suspend fun listTeams(): List<HoppTeam> {
        if (token.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val query = """{ myTeams { id name } }"""
                val response = executeGraphQL(query) ?: return@withContext emptyList()
                val data = response.getAsJsonObject("data") ?: return@withContext emptyList()
                val teamsArray = data.getAsJsonArray("myTeams") ?: return@withContext emptyList()
                teamsArray.map { element ->
                    val obj = element.asJsonObject
                    HoppTeam(
                        id = obj.get("id").asString,
                        name = obj.get("name").asString
                    )
                }
            } catch (e: Exception) {
                LOG.warn("Failed to list Hoppscotch teams", e)
                emptyList()
            }
        }
    }

    suspend fun listCollections(teamId: String? = null): List<HoppCollectionInfo> {
        if (token.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val query = if (teamId != null) {
                    """{ rootCollectionsOfTeam(teamID: "$teamId") { id title } }"""
                } else {
                    """{ rootCollectionsOfTeam { id title } }"""
                }
                val response = executeGraphQL(query) ?: return@withContext emptyList()
                val data = response.getAsJsonObject("data") ?: return@withContext emptyList()
                val collectionsArray = data.getAsJsonArray("rootCollectionsOfTeam")
                    ?: return@withContext emptyList()
                collectionsArray.map { element ->
                    val obj = element.asJsonObject
                    HoppCollectionInfo(
                        id = obj.get("id").asString,
                        name = obj.get("title").asString
                    )
                }
            } catch (e: Exception) {
                LOG.warn("Failed to list Hoppscotch collections", e)
                emptyList()
            }
        }
    }

    suspend fun uploadCollection(collection: HoppCollection, teamId: String? = null): HoppUploadResult {
        if (token.isBlank()) {
            return HoppUploadResult(success = false, message = "No access token configured")
        }
        return withContext(Dispatchers.IO) {
            try {
                // Transform to personal collection format: move auth/headers/variables/etc.
                // into a "data" sub-object, matching Hoppscotch's translateToPersonalCollectionFormat.
                val transformed = translateToPersonalCollectionFormat(collection)
                // Server expects a JSON array of CollectionFolder objects
                val jsonArray = JsonArray()
                jsonArray.add(transformed)
                val collectionJson = gson.toJson(jsonArray)
                val escapedJson = gson.toJson(collectionJson)

                val mutation = if (teamId != null) {
                    """mutation { importCollectionsFromJSON(
                        teamID: "$teamId",
                        jsonString: $escapedJson
                    ) }"""
                } else {
                    """mutation { importUserCollectionsFromJSON(
                        jsonString: $escapedJson,
                        reqType: REST
                    ) { exportedCollection collectionType } }"""
                }

                val response = executeGraphQL(mutation) ?: return@withContext HoppUploadResult(
                    success = false,
                    message = "Empty response from server"
                )

                if (response.has("errors")) {
                    val errors = response.getAsJsonArray("errors")
                    val message = errors?.firstOrNull()?.asJsonObject?.get("message")?.asString
                        ?: "Unknown GraphQL error"
                    HoppUploadResult(success = false, message = message)
                } else {
                    val data = response.getAsJsonObject("data")
                    if (teamId != null) {
                        // Team import returns Boolean
                        val success = data?.get("importCollectionsFromJSON")?.asBoolean ?: false
                        HoppUploadResult(
                            success = success,
                            message = if (success) "Collection uploaded to team successfully" else "Upload failed"
                        )
                    } else {
                        // User import returns { exportedCollection, collectionType }
                        val importResult = data?.getAsJsonObject("importUserCollectionsFromJSON")
                        val collectionId = importResult?.get("exportedCollection")?.asString
                        HoppUploadResult(
                            success = true,
                            message = "Collection uploaded successfully",
                            collectionId = collectionId
                        )
                    }
                }
            } catch (e: Exception) {
                LOG.warn("Failed to upload Hoppscotch collection", e)
                HoppUploadResult(success = false, message = e.message)
            }
        }
    }

    suspend fun updateCollection(collectionId: String, collection: HoppCollection): HoppUploadResult {
        if (token.isBlank()) {
            return HoppUploadResult(success = false, message = "No access token configured")
        }

        val uploadResult = uploadCollection(collection)
        if (uploadResult.success && uploadResult.collectionId != null) {
            deleteCollection(collectionId)
            return uploadResult.copy(message = "Collection updated successfully (new ID: ${uploadResult.collectionId})")
        }
        return uploadResult
    }

    suspend fun deleteCollection(collectionId: String, teamId: String? = null): Boolean {
        if (token.isBlank()) return false
        return withContext(Dispatchers.IO) {
            try {
                val mutation = if (teamId != null) {
                    """mutation { deleteCollection(collectionID: "$collectionId") }"""
                } else {
                    """mutation { deleteUserCollection(id: "$collectionId") }"""
                }
                val response = executeGraphQL(mutation)
                response != null && !response.has("errors")
            } catch (e: Exception) {
                LOG.warn("Failed to delete Hoppscotch collection", e)
                false
            }
        }
    }

    private val isCloudInstance: Boolean
        get() = isCloudServer(serverUrl)

    private suspend fun executeGraphQL(query: String): JsonObject? {
        val graphqlUrl = resolveGraphQLUrl(serverUrl, backendUrl)
        val body = gson.toJson(mapOf("query" to query))

        LOG.info("call:$graphqlUrl \nbody:$body")

        val request = HttpRequest(
            url = graphqlUrl,
            method = "POST",
            headers = listOf(
                KeyValue("Content-Type", "application/json"),
                KeyValue("Authorization", "Bearer $token")
            ),
            body = body
        )

        val response = httpClient.execute(request)

        if (response.code == 401) {
            LOG.warn("Hoppscotch API returned 401 Unauthorized")
            throw HoppscotchAuthException("Authentication failed - token may be expired")
        }

        if (response.code == 405) {
            LOG.warn("Hoppscotch API returned 405 Method Not Allowed - check endpoint URL")
            return null
        }

        if (response.code != 200) {
            LOG.warn("Hoppscotch API returned HTTP ${response.code}")
            return null
        }

        return try {
            gson.fromJson(response.body, JsonObject::class.java)
        } catch (e: Exception) {
            LOG.warn("Failed to parse Hoppscotch API response", e)
            null
        }
    }

    /**
     * Transforms a [HoppCollection] into the "personal collection" format expected
     * by Hoppscotch's `importUserCollectionsFromJSON` / `importCollectionsFromJSON` mutations.
     *
     * This mirrors the frontend's `translateToPersonalCollectionFormat`:
     * - `auth`, `headers`, `variables`, `description`, `preRequestScript`, `testScript`
     *   are moved into a `data` sub-object
     * - `v`, `name`, `_ref_id`, `folders`, `requests` remain at the top level
     * - `folders` are recursively transformed
     */
    private fun translateToPersonalCollectionFormat(collection: HoppCollection): JsonObject {
        val obj = JsonObject()

        obj.addProperty("v", collection.v)
        obj.addProperty("name", collection.name)
        obj.addProperty("_ref_id", collection._ref_id)

        // Recursively transform folders
        val foldersArray = JsonArray()
        collection.folders.forEach { folder ->
            foldersArray.add(translateToPersonalCollectionFormat(folder))
        }
        obj.add("folders", foldersArray)

        // Requests stay at top level as-is
        obj.add("requests", gson.toJsonTree(collection.requests))

        // Move auth/headers/variables/description/scripts into "data" sub-object
        val data = JsonObject()
        data.add("auth", gson.toJsonTree(collection.auth))
        data.add("headers", gson.toJsonTree(collection.headers))
        data.add("variables", gson.toJsonTree(collection.variables))
        data.addProperty("description", collection.description ?: "")
        data.addProperty("preRequestScript", collection.preRequestScript)
        data.addProperty("testScript", collection.testScript)
        obj.add("data", data)

        return obj
    }

    companion object : IdeaLog {
        private const val DEFAULT_SERVER_URL = "https://hoppscotch.io"
        private const val CLOUD_SERVER_HOST = "hoppscotch.io"
        private const val CLOUD_API_BASE_URL = "https://api.hoppscotch.io"

        /**
         * Resolves the API base URL for a given Hoppscotch server URL.
         *
         * - For the cloud instance (`https://hoppscotch.io`), returns `https://api.hoppscotch.io`
         * - For self-hosted instances with a configured backend URL, returns the backend URL
         * - For self-hosted instances without a backend URL, returns the server URL
         *
         * The backend URL corresponds to Hoppscotch's `VITE_BACKEND_API_URL` env variable,
         * e.g., `http://localhost:3170/v1` for a default self-hosted setup.
         */
        fun resolveApiBaseUrl(serverUrl: String, backendUrl: String? = null): String {
            val normalized = serverUrl.trimEnd('/')
            val host = try {
                java.net.URI(normalized).host
            } catch (e: Exception) {
                normalized
            }
            if (host == CLOUD_SERVER_HOST) {
                return CLOUD_API_BASE_URL
            }
            if (!backendUrl.isNullOrBlank()) {
                return backendUrl.trimEnd('/')
            }
            return normalized
        }

        /**
         * Resolves the REST API v1 base URL for a given Hoppscotch server URL.
         *
         * This corresponds to Hoppscotch's `VITE_BACKEND_API_URL` env variable:
         * - Cloud: `https://api.hoppscotch.io/v1`
         * - Self-hosted with backend URL: returns the backend URL as-is (already includes `/v1`)
         * - Self-hosted without backend URL: `{serverUrl}/v1`
         *
         * Auth endpoints like `/auth/signin` and `/auth/verify` are under this base.
         */
        fun resolveApiV1BaseUrl(serverUrl: String, backendUrl: String? = null): String {
            val apiBase = resolveApiBaseUrl(serverUrl, backendUrl)
            // For cloud, resolveApiBaseUrl returns https://api.hoppscotch.io (no /v1)
            // For self-hosted with backendUrl, it already includes /v1 (e.g., http://localhost:3170/v1)
            // For self-hosted without backendUrl, it returns the server URL (no /v1)
            if (isCloudServer(serverUrl)) {
                return "$apiBase/v1"
            }
            if (!backendUrl.isNullOrBlank()) {
                // Backend URL already includes /v1
                return apiBase
            }
            return "$apiBase/v1"
        }

        /**
         * Resolves the GraphQL endpoint URL.
         *
         * - Cloud: `https://api.hoppscotch.io/graphql`
         * - Self-hosted with backend URL: `{backendUrl}/graphql`
         * - Self-hosted without backend URL: `{serverUrl}/graphql`
         *
         * This matches the Hoppscotch frontend's `VITE_BACKEND_GQL_URL` default:
         * `https://api.hoppscotch.io/graphql` for cloud,
         * `http://localhost:3170/graphql` for self-hosted
         */
        fun resolveGraphQLUrl(serverUrl: String, backendUrl: String? = null): String {
            val apiBase = resolveApiBaseUrl(serverUrl, backendUrl)
            return "$apiBase/graphql"
        }

        fun isCloudServer(serverUrl: String): Boolean {
            val normalized = serverUrl.trimEnd('/')
            val host = try {
                java.net.URI(normalized).host
            } catch (e: Exception) {
                normalized
            }
            return host == CLOUD_SERVER_HOST
        }
    }
}

/**
 * A Hoppscotch team (workspace).
 *
 * @property id unique team identifier
 * @property name display name of the team
 */
data class HoppTeam(
    val id: String,
    val name: String
)

/**
 * Summary info for a Hoppscotch collection returned by list queries.
 *
 * @property id unique collection identifier
 * @property name display name (title) of the collection
 */
data class HoppCollectionInfo(
    val id: String,
    val name: String
)

/**
 * Result of a Hoppscotch collection upload or update operation.
 *
 * @property success whether the operation succeeded
 * @property message human-readable result or error message
 * @property collectionId the ID of the created collection (on success)
 */
data class HoppUploadResult(
    val success: Boolean,
    val message: String? = null,
    val collectionId: String? = null
)

/**
 * Thrown when the Hoppscotch API returns a 401 Unauthorized response.
 *
 * This indicates the access token has expired or is invalid.
 * Callers should attempt token refresh via [HoppscotchAuthService.refreshToken]
 * or prompt the user to re-authenticate.
 *
 * @param message description of the authentication failure
 */
class HoppscotchAuthException(message: String) : Exception(message)
