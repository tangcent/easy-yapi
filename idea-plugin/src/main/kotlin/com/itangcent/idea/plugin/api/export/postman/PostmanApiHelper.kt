package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.ImplementedBy
import com.itangcent.idea.plugin.api.export.postman.Emojis.PERSONAL
import com.itangcent.idea.plugin.api.export.postman.Emojis.TEAM

/**
 * A helper class provides methods to manage collections and workspaces in Postman.
 */
@ImplementedBy(DefaultPostmanApiHelper::class)
interface PostmanApiHelper {
    /**
     * Creates a new collection in Postman.
     * @param collection The collection details to create.
     * @param workspaceId Optional workspace ID where the collection will be created.
     * @return The created collection details or null if creation failed.
     */
    fun createCollection(collection: HashMap<String, Any?>, workspaceId: String?): Map<String, Any?>?

    /**
     * Updates an existing collection in Postman.
     * @param collectionId The ID of the collection to update.
     * @param collectionInfo The new collection details.
     * @return True if the update was successful, false otherwise.
     */
    fun updateCollection(collectionId: String, collectionInfo: Map<String, Any?>): Boolean

    /**
     * Deletes a collection in Postman.
     * @param collectionId The ID of the collection to delete.
     * @return The deleted collection details or null if deletion failed.
     */
    fun deleteCollectionInfo(collectionId: String): Map<String, Any?>?

    /**
     * Retrieves all collections from Postman.
     * @return A list of all collections or null if retrieval failed.
     */
    fun getAllCollection(): List<Map<String, Any?>>?

    /**
     * Retrieves all collections within a specific workspace from Postman.
     * @param workspaceId The ID of the workspace to retrieve collections from.
     * @return A list of collections in the specified workspace or null if retrieval failed.
     */
    fun getCollectionByWorkspace(workspaceId: String): List<Map<String, Any?>>?

    /**
     * Retrieves information about a specific collection from Postman.
     * @param collectionId The ID of the collection to retrieve.
     * @return The collection details or null if retrieval failed.
     */
    fun getCollectionInfo(collectionId: String): Map<String, Any?>?

    /**
     * Retrieves all workspaces from Postman.
     * @return A list of all workspaces or null if retrieval failed.
     */
    fun getAllWorkspaces(): List<PostmanWorkspace>?

    /**
     * Retrieves information about a specific workspace from Postman.
     * @param workspaceId The ID of the workspace to retrieve.
     * @return The workspace details or null if retrieval failed.
     */
    fun getWorkspaceInfo(workspaceId: String): PostmanWorkspace?
}

class PostmanWorkspace {
    var id: String? = null
    var name: String? = null
    var type: String? = null

    constructor(id: String?, name: String?, type: String?) {
        this.id = id
        this.name = name
        this.type = type
    }

    constructor()

    fun nameWithType(): String? {
        if (type == "team") {
            return "${TEAM}$name"
        } else if (type == "personal") {
            return "${PERSONAL}$name"
        }
        return name
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PostmanWorkspace

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }
}