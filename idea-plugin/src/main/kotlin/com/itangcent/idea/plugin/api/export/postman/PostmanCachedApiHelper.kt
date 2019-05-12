package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.google.inject.name.Named
import com.itangcent.idea.binder.DbBeanBinderFactory
import com.itangcent.intellij.file.LocalFileRepository

class PostmanCachedApiHelper : DefaultPostmanApiHelper() {

    @Inject
    @Named("projectCacheRepository")
    private val projectCacheRepository: LocalFileRepository? = null

    private var dbBeanBinderFactory: DbBeanBinderFactory<CollectionInfoCache>? = null

    private fun getDbBeanBinderFactory(): DbBeanBinderFactory<CollectionInfoCache> {
        if (dbBeanBinderFactory == null) {
            synchronized(this) {
                dbBeanBinderFactory = DbBeanBinderFactory(projectCacheRepository!!.getOrCreateFile(".api.postman.v1.1.db").path)
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
                .getBeanBinder("getAllCollection")
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
                .getBeanBinder("collection:$collectionId")
                .save(cacheOfCollection)
        //endregion add cache of created collection--------------------------------

        return createdCollection

    }

    override fun updateCollection(collectionId: String, apiInfo: HashMap<String, Any?>): Boolean {
        if (super.updateCollection(collectionId, apiInfo)) {
            val collectionDetailBeanBinder = getDbBeanBinderFactory().getBeanBinder("collection:$collectionId")
            val cache = CollectionInfoCache()
            cache.collectionDetail = apiInfo
            collectionDetailBeanBinder.save(cache)
            return true
        }
        return false
    }

    fun getAllCollection(useCache: Boolean = true): ArrayList<HashMap<String, Any?>>? {
        if (useCache) {
            val allCollectionBeanBinder = getDbBeanBinderFactory().getBeanBinder("getAllCollection")
            val cache = allCollectionBeanBinder.read()
            if (cache != NULL_COLLECTION_INFO_CACHE) {
                return cache.allCollection
            }
        }

        val allCollection = super.getAllCollection()
        val cache = CollectionInfoCache()
        cache.allCollection = allCollection
        getDbBeanBinderFactory()
                .getBeanBinder("getAllCollection")
                .save(cache)
        return allCollection
    }

    fun getCollectionInfo(collectionId: String, useCache: Boolean = true): HashMap<String, Any?>? {
        if (useCache) {
            val collectionDetailBeanBinder = getDbBeanBinderFactory().getBeanBinder("collection:$collectionId")
            val cache = collectionDetailBeanBinder.read()
            if (cache != NULL_COLLECTION_INFO_CACHE) {
                return cache.collectionDetail
            }
        }

        val collectionDetail = super.getCollectionInfo(collectionId)
        val cache = CollectionInfoCache()
        cache.collectionDetail = collectionDetail
        getDbBeanBinderFactory()
                .getBeanBinder("collection:$collectionId")
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