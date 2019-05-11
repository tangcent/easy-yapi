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

    fun getAllCollection(): ArrayList<Map<String, Any?>>? {
        val allCollectionBeanBinder = getDbBeanBinderFactory().getBeanBinder("getAllCollection")
        var cache = allCollectionBeanBinder.read()
        if (cache != NULL_COLLECTION_INFO_CACHE) {
            return cache.allCollection
        }

        val allCollection = postmanApiHelper!!.getAllCollection()
        cache = CollectionInfoCache()
        cache.allCollection = allCollection
        allCollectionBeanBinder.save(cache)
        return allCollection
    }

    fun getCollectionInfo(collectionId: String): HashMap<String, Any?>? {
        val collectionDetailBeanBinder = getDbBeanBinderFactory().getBeanBinder("collection:$collectionId")
        var cache = collectionDetailBeanBinder.read()
        if (cache != NULL_COLLECTION_INFO_CACHE) {
            return cache.collectionDetail
        }

        val collectionDetail = postmanApiHelper!!.getCollectionInfo(collectionId)
        cache = CollectionInfoCache()
        cache.collectionDetail = collectionDetail
        collectionDetailBeanBinder.save(cache)
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