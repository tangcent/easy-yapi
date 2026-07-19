package com.itangcent.easyapi.channel.postman

import com.itangcent.easyapi.core.export.ExportMetadata
import com.itangcent.easyapi.channel.postman.model.PostmanCollection
import com.itangcent.easyapi.core.util.text.append

data class PostmanExportMetadata(
    val workspaceName: String? = null,
    val workspaceId: String? = null,
    val collectionName: String? = null,
    val collectionId: String? = null,
    val collectionData: PostmanCollection? = null
) : ExportMetadata {
    override fun formatDisplay(): String? {
        if (collectionData != null) {
            return null
        }

        val workspaceDisplay = workspaceName ?: workspaceId
        val collectionDisplay = collectionName ?: collectionId

        return workspaceDisplay.append(collectionDisplay, separator = "/")
    }
}
