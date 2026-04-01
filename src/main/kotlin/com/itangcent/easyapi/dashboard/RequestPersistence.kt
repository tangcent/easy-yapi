package com.itangcent.easyapi.dashboard

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.cache.ProjectCacheRepository
import com.itangcent.easyapi.util.GsonUtils

/**
 * Data class representing a persisted API request configuration.
 * 
 * @property endpointKey Unique identifier for the source endpoint
 * @property url The request URL
 * @property method The HTTP method
 * @property headers Map of request headers
 * @property body Optional request body content
 */
data class PersistedRequest(
    val endpointKey: String,
    val url: String,
    val method: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null
)

/**
 * Handles persistence of API request configurations.
 * 
 * This class provides functionality to save and load request configurations
 * to/from a JSON file in the project cache directory. Useful for preserving
 * user-configured requests across IDE sessions.
 * 
 * @param project The IntelliJ project context
 */
class RequestPersistence(project: Project) {
    /** Repository for accessing project cache storage */
    private val repo = ProjectCacheRepository.getInstance(project)
    /** Key for the persisted requests file */
    private val key = "dashboard_requests.json"

    /**
     * Loads all persisted request configurations.
     * 
     * @return List of persisted requests, empty list if none found
     */
    fun loadAll(): List<PersistedRequest> {
        val raw = repo.read(key) ?: return emptyList()
        return runCatching { GsonUtils.fromJson<List<PersistedRequest>>(raw) }.getOrNull().orEmpty()
    }

    /**
     * Saves all request configurations to persistent storage.
     * 
     * @param requests The list of requests to persist
     */
    fun saveAll(requests: List<PersistedRequest>) {
        repo.write(key, GsonUtils.toJson(requests))
    }

    /**
     * Clears all persisted request configurations.
     */
    fun reset() {
        repo.delete(key)
    }
}
