package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.idea.binder.DbBeanBinderFactory
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.intellij.logger.Logger
import kotlin.streams.toList

class PostmanCachedApiHelper : DefaultPostmanApiHelper() {

    @Inject
    private val logger: Logger? = null

    @Inject
    private val localFileRepository: LocalFileRepository? = null

    private var dbBeanBinderFactory: DbBeanBinderFactory<CollectionInfoCache>? = null

    private fun getDbBeanBinderFactory(): DbBeanBinderFactory<CollectionInfoCache> {
        if (dbBeanBinderFactory == null) {
            synchronized(this) {
                dbBeanBinderFactory = DbBeanBinderFactory(localFileRepository!!.getOrCreateFile(".api.postman.v1.1.db").path)
                { NULL_COLLECTION_INFO_CACHE }
            }
        }
        return this.dbBeanBinderFactory!!
    }

    override fun createCollection(collection: HashMap<String, Any?>): HashMap<String, Any?>? {
        val createdCollection: HashMap<String, Any?> = super.createCollection(collection) ?: return null

        val collectionId = createdCollection["id"]?.toString() ?: return null

        collection["id"] = collectionId

        //region update collection of AllCollection-----------------------------
        val allCollectionBeanBinder = getDbBeanBinderFactory()
                .getBeanBinder("${getPrivateToken()}_getAllCollection")
        val cacheOfAllCollection = allCollectionBeanBinder.read()
        if (cacheOfAllCollection != NULL_COLLECTION_INFO_CACHE) {
            if (cacheOfAllCollection.allCollection == null) {
                cacheOfAllCollection.allCollection = ArrayList()
            }
            cacheOfAllCollection.allCollection!!.add(createdCollection)
            allCollectionBeanBinder.save(cacheOfAllCollection)
        }
        //endregion update collection of AllCollection-----------------------------

        //region add cache of created collection--------------------------------
        val cacheOfCollection = CollectionInfoCache()
        cacheOfCollection.collectionDetail = collection

        getDbBeanBinderFactory()
                .getBeanBinder("${getPrivateToken()}_collection:$collectionId")
                .save(cacheOfCollection)
        //endregion add cache of created collection--------------------------------

        return createdCollection

    }

    @Suppress("UNCHECKED_CAST")
    override fun updateCollection(collectionId: String, collectionInfo: HashMap<String, Any?>): Boolean {
        if (super.updateCollection(collectionId, collectionInfo)) {
            //region try update collection of AllCollection-----------------------------
            val allCollectionBeanBinder = getDbBeanBinderFactory()
                    .getBeanBinder("${getPrivateToken()}_getAllCollection")
            val cacheOfAllCollection = allCollectionBeanBinder.read()
            if (cacheOfAllCollection != NULL_COLLECTION_INFO_CACHE) {
                if (cacheOfAllCollection.allCollection != null) {
                    val collectionInAllCollectionCache = cacheOfAllCollection.allCollection!!.firstOrNull { it["id"] == collectionId }
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

            val collectionDetailBeanBinder = getDbBeanBinderFactory().getBeanBinder("${getPrivateToken()}_collection:$collectionId")
            val cache = CollectionInfoCache()
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
                    .getBeanBinder("${getPrivateToken()}_getAllCollection")
            val cacheOfAllCollection = allCollectionBeanBinder.read()
            if (cacheOfAllCollection != NULL_COLLECTION_INFO_CACHE) {
                if (cacheOfAllCollection.allCollection != null) {
                    if (cacheOfAllCollection.allCollection!!.removeIf {
                                it["id"] == collectionId
                            }) {
                        allCollectionBeanBinder.save(cacheOfAllCollection)
                    }
                }
            }
            //endregion update collection of AllCollection-----------------------------

            getDbBeanBinderFactory().deleteBinder("${getPrivateToken()}_collection:$collectionId")
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
                    "${getPrivateToken()}_getAllCollection")
            val cache = allCollectionBeanBinder.read()
            if (cache != NULL_COLLECTION_INFO_CACHE) {
                return cache.allCollection
            }
        }

        val allCollection = super.getAllCollection()
        if (allCollection == null) return allCollection
        val cache = CollectionInfoCache()
        cache.allCollection = allCollection


        val beanBinder = getDbBeanBinderFactory()
                .getBeanBinder("${getPrivateToken()}_getAllCollection")

        if (!useCache) { //try clean cache
            val oldCollectionCache = beanBinder.read()

            if (oldCollectionCache != NULL_COLLECTION_INFO_CACHE
                    && oldCollectionCache.allCollection.notNullOrEmpty()) {
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
                    getDbBeanBinderFactory().deleteBinder("${getPrivateToken()}_collection:$it")
                }
            }
        }

        beanBinder.save(cache)

        return allCollection
    }

    fun getCollectionInfo(collectionId: String, useCache: Boolean = true): HashMap<String, Any?>? {
        if (useCache) {
            val collectionDetailBeanBinder = getDbBeanBinderFactory().getBeanBinder("${getPrivateToken()}_collection:$collectionId")
            val cache = collectionDetailBeanBinder.read()
            if (cache != NULL_COLLECTION_INFO_CACHE) {
                return cache.collectionDetail
            }
        }

        val collectionDetail = super.getCollectionInfo(collectionId)
        val cache = CollectionInfoCache()
        cache.collectionDetail = collectionDetail
        getDbBeanBinderFactory()
                .getBeanBinder("${getPrivateToken()}_collection:$collectionId")
                .save(cache)
        return collectionDetail
    }

    class CollectionInfoCache {
        var allCollection: ArrayList<HashMap<String, Any?>>? = null

        var collectionDetail: HashMap<String, Any?>? = null
    }

    companion object {
        val NULL_COLLECTION_INFO_CACHE = CollectionInfoCache()
    }
}