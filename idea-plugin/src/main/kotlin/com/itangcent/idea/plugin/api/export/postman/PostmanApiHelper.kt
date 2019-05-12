package com.itangcent.idea.plugin.api.export.postman

interface PostmanApiHelper {
    fun hasPrivateToken(): Boolean

    fun getPrivateToken(): String?

    /**
     * @return collection id
     */
    fun createCollection(collection: HashMap<String, Any?>): HashMap<String, Any?>?

    fun updateCollection(collectionId: String, apiInfo: HashMap<String, Any?>): Boolean

    fun getAllCollection(): ArrayList<HashMap<String, Any?>>?

    fun getCollectionInfo(collectionId: String): HashMap<String, Any?>?
}