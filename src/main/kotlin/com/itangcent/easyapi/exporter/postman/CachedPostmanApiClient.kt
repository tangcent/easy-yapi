package com.itangcent.easyapi.exporter.postman

import com.itangcent.easyapi.util.GsonUtils
import com.itangcent.easyapi.cache.AppCacheRepository
import com.itangcent.easyapi.logging.IdeaLog

/**
 * Cached wrapper for PostmanApiClient.
 *
 * Caches API responses to reduce API calls and improve performance:
 * - Workspaces list
 * - Collections list per workspace
 *
 * Cache is stored using AppCacheRepository and persists across sessions.
 *
 * @see PostmanApiClient for the underlying API client
 * @see AppCacheRepository for cache storage
 */
class CachedPostmanApiClient(
    private val postmanClient: PostmanApiClient
) {

    companion object : IdeaLog {
        private const val WORKSPACES_CACHE_KEY = "postman/workspaces.json"
        private const val COLLECTIONS_CACHE_KEY_PREFIX = "postman/collections_"
    }

    suspend fun listWorkspaces(useCache: Boolean = true): List<Workspace> {
        LOG.info("CachedPostmanApiClient.listWorkspaces: useCache=$useCache")

        if (useCache) {
            val cached = loadCachedWorkspaces()
            if (cached.isNotEmpty()) {
                LOG.info("CachedPostmanApiClient.listWorkspaces: returning ${cached.size} cached workspaces")
                return cached
            }
        }

        val workspaces = postmanClient.listWorkspaces()
        if (workspaces.isNotEmpty()) {
            cacheWorkspaces(workspaces)
        }
        return workspaces
    }

    private fun cacheWorkspaces(workspaces: List<Workspace>) {
        try {
            AppCacheRepository.getInstance().write(WORKSPACES_CACHE_KEY, GsonUtils.toJson(workspaces))
            LOG.info("Cached ${workspaces.size} workspaces")
        } catch (e: Exception) {
            LOG.warn("Failed to cache workspaces", e)
        }
    }

    private fun loadCachedWorkspaces(): List<Workspace> {
        return try {
            val cached = AppCacheRepository.getInstance().read(WORKSPACES_CACHE_KEY)
            if (cached != null) {
                val arr = GsonUtils.fromJson<Array<Workspace>>(cached, Array<Workspace>::class.java)
                arr.toList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            LOG.warn("Failed to load cached workspaces", e)
            emptyList()
        }
    }

    fun clearWorkspacesCache() {
        AppCacheRepository.getInstance().delete(WORKSPACES_CACHE_KEY)
    }

    suspend fun listCollections(workspaceId: String, useCache: Boolean = true): List<PostmanCollectionInfo> {
        LOG.info("CachedPostmanApiClient.listCollections: workspaceId=$workspaceId, useCache=$useCache")

        val cacheKey = "${COLLECTIONS_CACHE_KEY_PREFIX}${workspaceId}.json"
        if (useCache) {
            val cached = loadCachedCollections(cacheKey)
            if (cached.isNotEmpty()) {
                LOG.info("CachedPostmanApiClient.listCollections: returning ${cached.size} cached collections")
                return cached
            }
        }

        val collections = postmanClient.listCollections(workspaceId)
        if (collections.isNotEmpty()) {
            cacheCollections(cacheKey, collections)
        }
        return collections
    }

    private fun cacheCollections(cacheKey: String, collections: List<PostmanCollectionInfo>) {
        try {
            AppCacheRepository.getInstance().write(cacheKey, GsonUtils.toJson(collections))
            LOG.info("Cached ${collections.size} collections")
        } catch (e: Exception) {
            LOG.warn("Failed to cache collections", e)
        }
    }

    private fun loadCachedCollections(cacheKey: String): List<PostmanCollectionInfo> {
        return try {
            val cached = AppCacheRepository.getInstance().read(cacheKey)
            if (cached != null) {
                val arr = GsonUtils.fromJson<Array<PostmanCollectionInfo>>(cached, Array<PostmanCollectionInfo>::class.java)
                arr.toList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            LOG.warn("Failed to load cached collections", e)
            emptyList()
        }
    }

    suspend fun uploadCollection(collection: com.itangcent.easyapi.exporter.postman.model.PostmanCollection): UploadResult {
        return postmanClient.uploadCollection(collection)
    }

    suspend fun updateCollection(collectionUid: String, collection: com.itangcent.easyapi.exporter.postman.model.PostmanCollection): UploadResult {
        return postmanClient.updateCollection(collectionUid, collection)
    }
}

fun PostmanApiClient.asCached() = CachedPostmanApiClient(this)