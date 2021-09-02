package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.ImplementedBy
import com.itangcent.idea.plugin.api.export.postman.Emojis.PERSONAL
import com.itangcent.idea.plugin.api.export.postman.Emojis.TEAM

@ImplementedBy(DefaultPostmanApiHelper::class)
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