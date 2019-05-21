package com.itangcent.idea.plugin.api.export.postman

interface PostmanApiHelper {
    fun hasPrivateToken(): Boolean

    fun getPrivateToken(): String?

    fun setPrivateToken(postmanPrivateToken: String)

    /**
     * @return collection id
     */
    fun createCollection(collection: HashMap<String, Any?>): HashMap<String, Any?>?

    fun updateCollection(collectionId: String, collectionInfo: HashMap<String, Any?>): Boolean

    /**
     * On successful deletion of the collection, return the id and uid.
     */
    fun deleteCollectionInfo(collectionId: String): HashMap<String, Any?>?

    fun getAllCollection(): ArrayList<HashMap<String, Any?>>?

    fun getCollectionInfo(collectionId: String): HashMap<String, Any?>?
}