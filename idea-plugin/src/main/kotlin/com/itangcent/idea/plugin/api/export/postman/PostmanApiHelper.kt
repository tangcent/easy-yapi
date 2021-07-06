package com.itangcent.idea.plugin.api.export.postman

interface PostmanApiHelper {
    /**
     * @return collection id
     */
    fun createCollection(collection: HashMap<String, Any?>, workspaceId: String?): HashMap<String, Any?>?

    fun updateCollection(collectionId: String, collectionInfo: HashMap<String, Any?>): Boolean

    /**
     * On successful deletion of the collection, return the id and uid.
     */
    fun deleteCollectionInfo(collectionId: String): HashMap<String, Any?>?

    fun getAllCollection(): ArrayList<HashMap<String, Any?>>?

    fun getCollectionByWorkspace(workspaceId: String): List<HashMap<String, Any?>>?

    fun getCollectionInfo(collectionId: String): HashMap<String, Any?>?

    fun getAllWorkspaces(): List<PostmanWorkspace>?

    fun getWorkspaceInfo(workspaceId: String): PostmanWorkspace?
}

data class PostmanWorkspace(
    var id: String,
    var name: String,
    var type: String
)