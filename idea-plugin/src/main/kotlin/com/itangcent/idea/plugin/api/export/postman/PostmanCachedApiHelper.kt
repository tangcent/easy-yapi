package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.idea.binder.DbBeanBinderFactory
import com.itangcent.idea.plugin.settings.helper.PostmanSettingsHelper
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.intellij.logger.Logger
import kotlin.streams.toList

class PostmanCachedApiHelper : DefaultPostmanApiHelper() {

    @Inject
    private lateinit var logger: Logger

    @Inject
    private val localFileRepository: LocalFileRepository? = null

    @Inject
    private lateinit var postmanSettingsHelper: PostmanSettingsHelper

    private var dbBeanBinderFactory: DbBeanBinderFactory<PostmanInfoCache>? = null

    private fun getDbBeanBinderFactory(): DbBeanBinderFactory<PostmanInfoCache> {
        if (dbBeanBinderFactory == null) {
            synchronized(this) {
                dbBeanBinderFactory =
                    DbBeanBinderFactory(localFileRepository!!.getOrCreateFile(".api.postman.v1.1.db").path)
                    { NULL_POSTMAN_INFO_CACHE }
            }
        }
        return this.dbBeanBinderFactory!!
    }

    override fun createCollection(collection: HashMap<String, Any?>, workspaceId: String?): HashMap<String, Any?>? {
        val createdCollection: HashMap<String, Any?> = super.createCollection(collection, workspaceId) ?: return null

        val collectionId = createdCollection["id"]?.toString() ?: return null

        collection["id"] = collectionId

        //region update collection of AllCollection-----------------------------
        val allCollectionBeanBinder = getDbBeanBinderFactory()
            .getBeanBinder("${postmanSettingsHelper.getPrivateToken()}_getAllCollection")
        val cacheOfAllCollection = allCollectionBeanBinder.read()
        if (cacheOfAllCollection != NULL_POSTMAN_INFO_CACHE) {
            if (cacheOfAllCollection.allCollection == null) {
                cacheOfAllCollection.allCollection = ArrayList()
            }
            cacheOfAllCollection.allCollection!!.add(createdCollection)
            allCollectionBeanBinder.save(cacheOfAllCollection)
        }
        //endregion update collection of AllCollection-----------------------------

        //region add cache of created collection--------------------------------
        val cacheOfCollection = PostmanInfoCache()
        cacheOfCollection.collectionDetail = collection

        getDbBeanBinderFactory()
            .getBeanBinder("${postmanSettingsHelper.getPrivateToken()}_collection:$collectionId")
            .save(cacheOfCollection)
        //endregion add cache of created collection--------------------------------

        return createdCollection

    }

    @Suppress("UNCHECKED_CAST")
    override fun updateCollection(collectionId: String, collectionInfo: HashMap<String, Any?>): Boolean {
        if (super.updateCollection(collectionId, collectionInfo)) {
            //region try update collection of AllCollection-----------------------------
            val allCollectionBeanBinder = getDbBeanBinderFactory()
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
                getDbBeanBinderFactory().getBeanBinder("${postmanSettingsHelper.getPrivateToken()}_collection:$collectionId")
            val cache = PostmanInfoCache()
            cache.collectionDetail = collectionInfo
            collectionDetailBeanBinder.save(cache)
            return true
        }
        return false
    }

    override fun deleteCollectionInfo(collectionId: String): HashMap<String, Any?>? {
        val deleteCollectionInfo = super.deleteCollectionInfo(collectionId)
        if (deleteCollectionInfo != null) {

            //region update collection of AllCollection-----------------------------
            val allCollectionBeanBinder = getDbBeanBinderFactory()
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

            getDbBeanBinderFactory().deleteBinder("${postmanSettingsHelper.getPrivateToken()}_collection:$collectionId")
        }
        return deleteCollectionInfo
    }

    override fun getAllCollection(): ArrayList<HashMap<String, Any?>>? {
        return getAllCollection(true)
    }

    override fun getCollectionInfo(collectionId: String): HashMap<String, Any?>? {
        return getCollectionInfo(collectionId, true)
    }

    fun getAllCollection(useCache: Boolean): ArrayList<HashMap<String, Any?>>? {
        if (useCache) {
            val allCollectionBeanBinder = getDbBeanBinderFactory().getBeanBinder(
                "${postmanSettingsHelper.getPrivateToken()}_getAllCollection"
            )
            val cache = allCollectionBeanBinder.read()
            if (cache != NULL_POSTMAN_INFO_CACHE) {
                return cache.allCollection
            }
        }

        val allCollection = super.getAllCollection()
        if (allCollection == null) return allCollection
        val cache = PostmanInfoCache()
        cache.allCollection = allCollection


        val beanBinder = getDbBeanBinderFactory()
            .getBeanBinder("${postmanSettingsHelper.getPrivateToken()}_getAllCollection")

        if (!useCache) { //try clean cache
            val oldCollectionCache = beanBinder.read()

            if (oldCollectionCache != NULL_POSTMAN_INFO_CACHE
                && oldCollectionCache.allCollection.notNullOrEmpty()
            ) {
                val survivedCollectionIds = allCollection.stream()
                    .map { it["id"] }
                    .filter { it != null }
                    .toList()

                val deletedCollections = oldCollectionCache
                    .allCollection!!
                    .stream()
                    .map { it["id"] }
                    .filter { it != null }
                    .filter { !survivedCollectionIds.contains(it) }
                    .map { it.toString() }
                    .toList()

                deletedCollections.forEach {
                    getDbBeanBinderFactory().deleteBinder("${postmanSettingsHelper.getPrivateToken()}_collection:$it")
                }
            }
        }

        beanBinder.save(cache)

        return allCollection
    }

    fun getCollectionInfo(collectionId: String, useCache: Boolean = true): HashMap<String, Any?>? {
        if (useCache) {
            val collectionDetailBeanBinder =
                getDbBeanBinderFactory().getBeanBinder("${postmanSettingsHelper.getPrivateToken()}_collection:$collectionId")
            val cache = collectionDetailBeanBinder.read()
            if (cache != NULL_POSTMAN_INFO_CACHE) {
                return cache.collectionDetail
            }
        }

        val collectionDetail = super.getCollectionInfo(collectionId)
        val cache = PostmanInfoCache()
        cache.collectionDetail = collectionDetail
        getDbBeanBinderFactory()
            .getBeanBinder("${postmanSettingsHelper.getPrivateToken()}_collection:$collectionId")
            .save(cache)
        return collectionDetail
    }

    override fun getCollectionByWorkspace(workspaceId: String): ArrayList<HashMap<String, Any?>>? {
        return getCollectionByWorkspace(workspaceId, true)
    }

    fun getCollectionByWorkspace(workspaceId: String, useCache: Boolean): ArrayList<HashMap<String, Any?>>? {
        if (useCache) {
            val workspaceInfoBeanBinder = getDbBeanBinderFactory()
                .getBeanBinder("${postmanSettingsHelper.getPrivateToken()}_collectionByWorkspace:$workspaceId")
            val cache = workspaceInfoBeanBinder.read()
            if (cache != NULL_POSTMAN_INFO_CACHE) {
                LOG.debug("hit collection by workspace $workspaceId cache.")
                return cache.allCollection
            }
        }
        val allCollection = super.getCollectionByWorkspace(workspaceId)
        val cache = PostmanInfoCache()
        cache.allCollection = allCollection

        val beanBinder = getDbBeanBinderFactory()
            .getBeanBinder("${postmanSettingsHelper.getPrivateToken()}_collectionByWorkspace:$workspaceId")

        if (!useCache) { //try clean collection info cache
            val oldCollectionCache = beanBinder.read()
            if (oldCollectionCache != NULL_POSTMAN_INFO_CACHE
                && oldCollectionCache.allCollection.notNullOrEmpty()
            ) {
                val survivedCollectionIds = allCollection!!.mapNotNull { it["id"] }.toList()

                val deletedCollections = oldCollectionCache
                    .allCollection!!
                    .mapNotNull { it["id"] }
                    .filter { !survivedCollectionIds.contains(it) }
                    .map { it.toString() }
                    .toList()

                deletedCollections.forEach {
                    getDbBeanBinderFactory().deleteBinder("${postmanSettingsHelper.getPrivateToken()}_collection:$it")
                }
            }
        }

        beanBinder.save(cache)

        return allCollection
    }

    override fun getWorkspaceInfo(workspaceId: String): PostmanWorkspace? {
        val workspaceInfoBeanBinder = getDbBeanBinderFactory()
            .getBeanBinder("${postmanSettingsHelper.getPrivateToken()}_workspace:$workspaceId")
        var cache = workspaceInfoBeanBinder.read()
        if (cache != NULL_POSTMAN_INFO_CACHE) {
            return cache.workspaceDetail
        }
        val workspace = super.getWorkspaceInfo(workspaceId)
        cache = PostmanInfoCache()
        cache.workspaceDetail = workspace
        getDbBeanBinderFactory()
            .getBeanBinder("${postmanSettingsHelper.getPrivateToken()}_workspace:$workspaceId")
            .save(cache)
        return workspace
    }

    data class PostmanInfoCache(
        var allCollection: ArrayList<HashMap<String, Any?>>? = null,
        var collectionDetail: HashMap<String, Any?>? = null,
        var allWorkspace: List<PostmanWorkspace>? = null,
        var workspaceDetail: PostmanWorkspace? = null
    )

    companion object {
        val NULL_POSTMAN_INFO_CACHE = PostmanInfoCache()
    }
}

private val LOG = org.apache.log4j.Logger.getLogger(PostmanCachedApiHelper::class.java)