package com.itangcent.easyapi.exporter.postman

import com.itangcent.easyapi.exporter.model.ExportMetadata
import com.itangcent.easyapi.exporter.postman.model.PostmanCollection
import com.itangcent.easyapi.util.append

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
