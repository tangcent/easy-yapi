package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.google.inject.name.Named
import com.itangcent.idea.binder.DbBeanBinderFactory
import com.itangcent.intellij.file.LocalFileRepository

class PostmanCachedHelper {

    @Inject
    private val postmanApiHelper: PostmanApiHelper? = null

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

    fun hasPrivateToken(): Boolean {
        return postmanApiHelper!!.hasPrivateToken()
    }

    fun updateCollection(collectionId: String, apiInfo: HashMap<String, Any?>): Boolean {
        if (postmanApiHelper!!.updateCollection(collectionId, apiInfo)) {
            val collectionDetailBeanBinder = getDbBeanBinderFactory().getBeanBinder("collection:$collectionId")
            val cache = CollectionInfoCache()
            cache.collectionDetail = apiInfo
            collectionDetailBeanBinder.save(cache)
            return true
        }
        return false
    }

    fun getAllCollection(useCache: Boolean = true): ArrayList<Map<String, Any?>>? {
        if (useCache) {
            val allCollectionBeanBinder = getDbBeanBinderFactory().getBeanBinder("getAllCollection")
            val cache = allCollectionBeanBinder.read()
            if (cache != NULL_COLLECTION_INFO_CACHE) {
                return cache.allCollection
            }
        }

        val allCollection = postmanApiHelper!!.getAllCollection()
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

        val collectionDetail = postmanApiHelper!!.getCollectionInfo(collectionId)
        val cache = CollectionInfoCache()
        cache.collectionDetail = collectionDetail
        getDbBeanBinderFactory()
                .getBeanBinder("collection:$collectionId")
                .save(cache)
        return collectionDetail
    }

    class CollectionInfoCache {
        var allCollection: ArrayList<Map<String, Any?>>? = null

        var collectionDetail: HashMap<String, Any?>? = null
    }

    companion object {
        val NULL_COLLECTION_INFO_CACHE = CollectionInfoCache()
    }
}