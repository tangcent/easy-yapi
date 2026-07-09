package com.itangcent.easyapi.exporter.channel.hoppscotch

import com.itangcent.easyapi.cache.AppCacheRepository
import com.itangcent.easyapi.exporter.channel.hoppscotch.model.HoppCollection
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.util.json.GsonUtils

/**
 * Caching decorator for [HoppscotchApiClient].
 *
 * Caches [listTeams] and [listCollections] results in [AppCacheRepository] to reduce
 * API calls and improve UI responsiveness. Write operations (upload, update, delete)
 * are delegated directly to the underlying client without caching.
 *
 * Use the [asCached] extension function to create an instance:
 * ```kotlin
 * val client = HoppscotchApiClient(token, serverUrl, httpClient).asCached()
 * ```
 *
 * @property client the underlying API client to delegate to
 * @see HoppscotchApiClient
 */
class CachedHoppscotchApiClient(
    private val client: HoppscotchApiClient
) : IdeaLog {

    companion object : IdeaLog {
        private const val TEAMS_CACHE_KEY = "hoppscotch/teams.json"
        private const val COLLECTIONS_CACHE_KEY_PREFIX = "hoppscotch/collections_"
    }

    suspend fun listTeams(useCache: Boolean = true): List<HoppTeam> {
        if (useCache) {
            val cached = loadCachedTeams()
            if (cached.isNotEmpty()) {
                LOG.info("Returning ${cached.size} cached Hoppscotch teams")
                return cached
            }
        }

        val teams = client.listTeams()
        if (teams.isNotEmpty()) {
            cacheTeams(teams)
        }
        return teams
    }

    private fun cacheTeams(teams: List<HoppTeam>) {
        try {
            AppCacheRepository.getInstance().write(TEAMS_CACHE_KEY, GsonUtils.toJson(teams))
        } catch (e: Exception) {
            LOG.warn("Failed to cache Hoppscotch teams", e)
        }
    }

    private fun loadCachedTeams(): List<HoppTeam> {
        return try {
            val cached = AppCacheRepository.getInstance().read(TEAMS_CACHE_KEY)
            if (cached != null) {
                GsonUtils.fromJson<Array<HoppTeam>>(cached, Array<HoppTeam>::class.java).toList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            LOG.warn("Failed to load cached Hoppscotch teams", e)
            emptyList()
        }
    }

    fun clearTeamsCache() {
        AppCacheRepository.getInstance().delete(TEAMS_CACHE_KEY)
    }

    suspend fun listCollections(teamId: String? = null, useCache: Boolean = true): List<HoppCollectionInfo> {
        val cacheKey = if (teamId != null) {
            "${COLLECTIONS_CACHE_KEY_PREFIX}${teamId}.json"
        } else {
            "${COLLECTIONS_CACHE_KEY_PREFIX}personal.json"
        }

        if (useCache) {
            val cached = loadCachedCollections(cacheKey)
            if (cached.isNotEmpty()) {
                LOG.info("Returning ${cached.size} cached Hoppscotch collections")
                return cached
            }
        }

        val collections = client.listCollections(teamId)
        if (collections.isNotEmpty()) {
            cacheCollections(cacheKey, collections)
        }
        return collections
    }

    private fun cacheCollections(cacheKey: String, collections: List<HoppCollectionInfo>) {
        try {
            AppCacheRepository.getInstance().write(cacheKey, GsonUtils.toJson(collections))
        } catch (e: Exception) {
            LOG.warn("Failed to cache Hoppscotch collections", e)
        }
    }

    private fun loadCachedCollections(cacheKey: String): List<HoppCollectionInfo> {
        return try {
            val cached = AppCacheRepository.getInstance().read(cacheKey)
            if (cached != null) {
                GsonUtils.fromJson<Array<HoppCollectionInfo>>(cached, Array<HoppCollectionInfo>::class.java).toList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            LOG.warn("Failed to load cached Hoppscotch collections", e)
            emptyList()
        }
    }

    fun clearCollectionsCache(teamId: String? = null) {
        val cacheKey = if (teamId != null) {
            "${COLLECTIONS_CACHE_KEY_PREFIX}${teamId}.json"
        } else {
            "${COLLECTIONS_CACHE_KEY_PREFIX}personal.json"
        }
        AppCacheRepository.getInstance().delete(cacheKey)
    }

    suspend fun testConnection(): Boolean = client.testConnection()

    suspend fun uploadCollection(collection: HoppCollection, teamId: String? = null): HoppUploadResult {
        return client.uploadCollection(collection, teamId)
    }

    suspend fun updateCollection(collectionId: String, collection: HoppCollection): HoppUploadResult {
        return client.updateCollection(collectionId, collection)
    }

    suspend fun deleteCollection(collectionId: String, teamId: String? = null): Boolean {
        return client.deleteCollection(collectionId, teamId)
    }
}

/**
 * Wraps this [HoppscotchApiClient] with a caching decorator.
 */
fun HoppscotchApiClient.asCached() = CachedHoppscotchApiClient(this)
