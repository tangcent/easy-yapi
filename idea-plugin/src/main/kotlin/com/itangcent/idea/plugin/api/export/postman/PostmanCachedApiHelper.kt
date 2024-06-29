package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.cache.CacheSwitcher
import com.itangcent.common.logger.Log
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.idea.binder.DbBeanBinderFactory
import com.itangcent.idea.plugin.settings.helper.PostmanSettingsHelper
import com.itangcent.intellij.file.LocalFileRepository

/**
 * A helper class for interacting with the Postman API with caching capabilities.
 * This class extends the DefaultPostmanApiHelper to provide additional caching functionality
 * for various Postman API operations such as creating, updating, deleting, and retrieving collections.
 */
@Singleton
class PostmanCachedApiHelper : DefaultPostmanApiHelper(), CacheSwitcher {

    private var readCache: Boolean = true

    @Inject
    private val localFileRepository: LocalFileRepository? = null

    @Inject
    private lateinit var postmanSettingsHelper: PostmanSettingsHelper

    private val dbBeanBinderFactory: DbBeanBinderFactory<PostmanInfoCache> by lazy {
        DbBeanBinderFactory(localFileRepository!!.getOrCreateFile(".api.postman.v1.1.db").path) { NULL_POSTMAN_INFO_CACHE }
    }

    /**
     * Creates a new collection in Postman and updates the local cache.
     * @return The created collection details or null if creation failed.
     */
    override fun createCollection(collection: HashMap<String, Any?>, workspaceId: String?): Map<String, Any?>? {
        val createdCollection: Map<String, Any?> = super.createCollection(collection, workspaceId) ?: return null

        val collectionId = createdCollection["id"]?.toString() ?: return null

        collection["id"] = collectionId

        //region update collection of AllCollection-----------------------------
        val allCollectionBeanBinder = dbBeanBinderFactory
            .getBeanBinder("${postmanSettingsHelper.getPrivateToken()}_getAllCollection")
        val cacheOfAllCollection = allCollectionBeanBinder.read()
        if (cacheOfAllCollection != NULL_POSTMAN_INFO_CACHE) {
            if (cacheOfAllCollection.allCollection == null) {
                cacheOfAllCollection.allCollection = ArrayList()
            }
            cacheOfAllCollection.allCollection!!.add(createdCollection.toMutableMap())
            allCollectionBeanBinder.save(cacheOfAllCollection)
        }
        //endregion update collection of AllCollection-----------------------------

        //region add cache of created collection--------------------------------
        val cacheOfCollection = PostmanInfoCache()
        cacheOfCollection.collectionDetail = collection

        dbBeanBinderFactory
            .getBeanBinder("${postmanSettingsHelper.getPrivateToken()}_collection:$collectionId")
            .save(cacheOfCollection)
        //endregion add cache of created collection--------------------------------

        return createdCollection

    }

    /**
     * Updates an existing collection in Postman and updates the local cache.
     * @return True if the update was successful, false otherwise.
     */
    @Suppress("UNCHECKED_CAST")
    override fun updateCollection(collectionId: String, collectionInfo: Map<String, Any?>): Boolean {
        if (super.updateCollection(collectionId, collectionInfo)) {
            //region try update collection of AllCollection-----------------------------
            val allCollectionBeanBinder = dbBeanBinderFactory
                .getBeanBinder("${postmanSettingsHelper.getPrivateToken()}_getAllCollection")
            val cacheOfAllCollection = allCollectionBeanBinder.read()
            if (cacheOfAllCollection != NULL_POSTMAN_INFO_CACHE) {
                if (cacheOfAllCollection.allCollection != null) {
                    val collectionInAllCollectionCache =
                        cacheOfAllCollection.allCollection!!.firstOrNull { it["id"] == collectionId }
                    if (collectionInAllCollectionCache != null) {
                        val info = collectionInfo["info"] as MutableMap<String, Any?>
                        //check collection name
                        if (collectionInAllCollectionCache["name"] != info["name"]) {
                            collectionInAllCollectionCache["name"] = info["name"]
                            //update cache if collection name was updated
                            allCollectionBeanBinder.save(cacheOfAllCollection)
                        }
                    }
                }
            }
            //endregion try update collection of AllCollection-----------------------------

            val collectionDetailBeanBinder =
                dbBeanBinderFactory.getBeanBinder("${postmanSettingsHelper.getPrivateToken()}_collection:$collectionId")
            val cache = PostmanInfoCache()
            cache.collectionDetail = collectionInfo
            collectionDetailBeanBinder.save(cache)
            return true
        }
        return false
    }

    /**
     * Deletes a collection in Postman and updates the local cache.
     * @return The deleted collection details or null if deletion failed.
     */
    override fun deleteCollectionInfo(collectionId: String): Map<String, Any?>? {
        val deleteCollectionInfo = super.deleteCollectionInfo(collectionId)
        if (deleteCollectionInfo != null) {

            //region update collection of AllCollection-----------------------------
            val allCollectionBeanBinder = dbBeanBinderFactory
                .getBeanBinder("${postmanSettingsHelper.getPrivateToken()}_getAllCollection")
            val cacheOfAllCollection = allCollectionBeanBinder.read()
            if (cacheOfAllCollection != NULL_POSTMAN_INFO_CACHE) {
                if (cacheOfAllCollection.allCollection != null) {
                    if (cacheOfAllCollection.allCollection!!.removeIf {
                            it["id"] == collectionId
                        }) {
                        allCollectionBeanBinder.save(cacheOfAllCollection)
                    }
                }
            }
            //endregion update collection of AllCollection-----------------------------

            dbBeanBinderFactory.deleteBinder("${postmanSettingsHelper.getPrivateToken()}_collection:$collectionId")
        }
        return deleteCollectionInfo
    }

    /**
     * Retrieves all collections from Postman, optionally using the cache.
     * @return A list of all collections or null if retrieval failed.
     */
    override fun getAllCollection(): List<Map<String, Any?>>? {
        return getAllCollection(true)
    }

    /**
     * Retrieves all collections from Postman, with an option to use the cache.
     * @param useCache Whether to use the cached data or not.
     * @return A list of all collections or null if retrieval failed.
     */
    fun getAllCollection(useCache: Boolean): List<Map<String, Any?>>? {
        if (useCache && this.readCache) {
            val allCollectionBeanBinder = dbBeanBinderFactory.getBeanBinder(
                "${postmanSettingsHelper.getPrivateToken()}_getAllCollection"
            )
            val cache = allCollectionBeanBinder.read()
            if (cache != NULL_POSTMAN_INFO_CACHE) {
                LOG.debug("read cache of all collection")
                return cache.allCollection
            }
        }

        val allCollection = super.getAllCollection() ?: return null
        val cache = PostmanInfoCache()
        cache.allCollection = allCollection.map { it.toMutableMap() }.toMutableList()


        val beanBinder = dbBeanBinderFactory
            .getBeanBinder("${postmanSettingsHelper.getPrivateToken()}_getAllCollection")

        //try clean cache
        val oldCollectionCache = beanBinder.read()

        if (oldCollectionCache != NULL_POSTMAN_INFO_CACHE
            && oldCollectionCache.allCollection.notNullOrEmpty()
        ) {
            val survivedCollectionIds = allCollection.mapNotNull { it["id"] }

            val deletedCollections = oldCollectionCache
                .allCollection!!
                .asSequence()
                .mapNotNull { it["id"] }
                .filter { !survivedCollectionIds.contains(it) }
                .map { it.toString() }
                .toList()

            deletedCollections.forEach {
                dbBeanBinderFactory.deleteBinder("${postmanSettingsHelper.getPrivateToken()}_collection:$it")
            }
        }

        beanBinder.save(cache)

        return allCollection
    }

    /**
     * Retrieves information about a specific collection from Postman, optionally using the cache.
     * @return The collection details or null if retrieval failed.
     */
    override fun getCollectionInfo(collectionId: String): Map<String, Any?>? {
        return getCollectionInfo(collectionId, true)
    }

    /**
     * Retrieves information about a specific collection from Postman, with an option to use the cache.
     * @param collectionId The ID of the collection to retrieve.
     * @param useCache Whether to use the cached data or not.
     * @return The collection details or null if retrieval failed.
     */
    fun getCollectionInfo(collectionId: String, useCache: Boolean = true): Map<String, Any?>? {
        if (useCache && this.readCache) {
            val collectionDetailBeanBinder =
                dbBeanBinderFactory.getBeanBinder("${postmanSettingsHelper.getPrivateToken()}_collection:$collectionId")
            val cache = collectionDetailBeanBinder.read()
            if (cache != NULL_POSTMAN_INFO_CACHE) {
                LOG.debug("read cache of collection $collectionId")
                return cache.collectionDetail
            }
        }

        val collectionDetail = super.getCollectionInfo(collectionId)
        val cache = PostmanInfoCache()
        cache.collectionDetail = collectionDetail
        dbBeanBinderFactory
            .getBeanBinder("${postmanSettingsHelper.getPrivateToken()}_collection:$collectionId")
            .save(cache)
        return collectionDetail
    }

    /**
     * Retrieves all collections in a specific workspace from Postman, optionally using the cache.
     * @return A list of collections in the specified workspace or null if retrieval failed.
     */
    override fun getCollectionByWorkspace(workspaceId: String): List<Map<String, Any?>>? {
        return getCollectionByWorkspace(workspaceId, true)
    }

    /**
     * Retrieves all collections in a specific workspace from Postman, with an option to use the cache.
     * @param workspaceId The ID of the workspace to retrieve collections from.
     * @param useCache Whether to use the cached data or not.
     * @return A list of collections in the specified workspace or null if retrieval failed.
     */
    fun getCollectionByWorkspace(workspaceId: String, useCache: Boolean): List<Map<String, Any?>>? {
        if (useCache && this.readCache) {
            val workspaceInfoBeanBinder = dbBeanBinderFactory
                .getBeanBinder("${postmanSettingsHelper.getPrivateToken()}_collectionByWorkspace:$workspaceId")
            val cache = workspaceInfoBeanBinder.read()
            if (cache != NULL_POSTMAN_INFO_CACHE) {
                LOG.debug("read cache of collections in workspace $workspaceId")
                return cache.allCollection
            }
        }
        val collectionInWorkspace = super.getCollectionByWorkspace(workspaceId)
        val cache = PostmanInfoCache()
        cache.allCollection = collectionInWorkspace?.map { it.toMutableMap() }?.toMutableList()

        val beanBinder = dbBeanBinderFactory
            .getBeanBinder("${postmanSettingsHelper.getPrivateToken()}_collectionByWorkspace:$workspaceId")

        if (!useCache) { //try clean collection info cache
            val oldCollectionCache = beanBinder.read()
            if (oldCollectionCache != NULL_POSTMAN_INFO_CACHE
                && oldCollectionCache.allCollection.notNullOrEmpty()
            ) {
                val survivedCollectionIds = collectionInWorkspace!!.mapNotNull { it["id"] }.toList()

                val deletedCollections = oldCollectionCache
                    .allCollection!!
                    .mapNotNull { it["id"] }
                    .filter { !survivedCollectionIds.contains(it) }
                    .map { it.toString() }
                    .toList()

                deletedCollections.forEach {
                    dbBeanBinderFactory.deleteBinder("${postmanSettingsHelper.getPrivateToken()}_collection:$it")
                }
            }
        }

        beanBinder.save(cache)

        return collectionInWorkspace
    }


    /**
     * Retrieves all collections in a specific workspace from Postman, optionally using the cache.
     * @return A list of collections in the specified workspace or null if retrieval failed.
     */
    override fun getWorkspaceInfo(workspaceId: String): PostmanWorkspace? {
        val workspaceInfoBeanBinder = dbBeanBinderFactory
            .getBeanBinder("${postmanSettingsHelper.getPrivateToken()}_workspace:$workspaceId")
        var cache = workspaceInfoBeanBinder.read()
        if (cache != NULL_POSTMAN_INFO_CACHE) {
            LOG.info("read cache of workspace $workspaceId")
            return cache.workspaceDetail
        }
        val workspace = super.getWorkspaceInfo(workspaceId)
        cache = PostmanInfoCache()
        cache.workspaceDetail = workspace
        dbBeanBinderFactory
            .getBeanBinder("${postmanSettingsHelper.getPrivateToken()}_workspace:$workspaceId")
            .save(cache)
        return workspace
    }

    /**
     * Retrieves all workspaces from Postman, optionally using the cache.
     * @return A list of all workspaces or null if retrieval failed.
     */
    override fun getAllWorkspaces(): List<PostmanWorkspace>? {
        return getAllWorkspaces(true)
    }

    /**
     * Retrieves all workspaces from Postman, with an option to use the cache.
     * @param useCache Whether to use the cached data or not.
     * @return A list of all workspaces or null if retrieval failed.
     */
    fun getAllWorkspaces(useCache: Boolean): List<PostmanWorkspace>? {
        if (useCache && this.readCache) {
            val allWorkspacesBeanBinder = dbBeanBinderFactory.getBeanBinder(
                "${postmanSettingsHelper.getPrivateToken()}_getAllWorkspaces"
            )
            val cache = allWorkspacesBeanBinder.read()
            if (cache != NULL_POSTMAN_INFO_CACHE) {
                LOG.info("read cache of allWorkspaces")
                return cache.allWorkspace
            }
        }

        val allWorkspaces = super.getAllWorkspaces() ?: return null
        val cache = PostmanInfoCache()
        cache.allWorkspace = allWorkspaces

        val beanBinder = dbBeanBinderFactory
            .getBeanBinder("${postmanSettingsHelper.getPrivateToken()}_getAllWorkspaces")

        //try clean cache
        val oldCollectionCache = beanBinder.read()

        if (oldCollectionCache != NULL_POSTMAN_INFO_CACHE
            && oldCollectionCache.allWorkspace.notNullOrEmpty()
        ) {
            val survivedCollectionIds = allWorkspaces
                .mapNotNull { it.id }

            val deletedCollections = oldCollectionCache
                .allWorkspace!!
                .asSequence()
                .mapNotNull { it.id }
                .filter { !survivedCollectionIds.contains(it) }
                .toList()

            deletedCollections.forEach {
                dbBeanBinderFactory.deleteBinder("${postmanSettingsHelper.getPrivateToken()}_workspace:$it")
            }
        }

        beanBinder.save(cache)

        return allWorkspaces
    }

    /**
     * Disables the use of cache for subsequent operations.
     */
    override fun notUserCache() {
        readCache = false
    }

    /**
     * Enables the use of cache for subsequent operations.
     */
    override fun userCache() {
        readCache = true
    }

    /**
     * Data class for caching Postman API responses.
     */
    data class PostmanInfoCache(
        var allCollection: MutableList<MutableMap<String, Any?>>? = null,
        var collectionDetail: Map<String, Any?>? = null,
        var allWorkspace: List<PostmanWorkspace>? = null,
        var workspaceDetail: PostmanWorkspace? = null
    )

    companion object : Log() {
        val NULL_POSTMAN_INFO_CACHE = PostmanInfoCache()
    }
}