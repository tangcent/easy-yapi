package com.itangcent.easyapi.channel.hoppscotch

import com.itangcent.easyapi.channel.hoppscotch.model.HoppCollection
import com.itangcent.easyapi.core.export.ExportMetadata
import com.itangcent.easyapi.core.util.text.append

/**
 * Export metadata for Hoppscotch channel results.
 *
 * Carries information about the exported collection through the export pipeline.
 * Two usage patterns:
 * - **File export** — [collectionData] is populated with the [HoppCollection] object
 *   (not serialized), and [formatDisplay] returns null (file path is shown instead)
 * - **Cloud upload** — [collectionName], [workspaceName], and [collectionId] are populated,
 *   and [formatDisplay] returns a "workspace/collection" display string
 *
 * @property collectionName the name of the exported collection
 * @property collectionData the collection object (for file export mode)
 * @property workspaceName the Hoppscotch workspace/team name (for cloud upload mode)
 * @property collectionId the Hoppscotch collection ID (for cloud upload mode)
 */
data class HoppscotchExportMetadata(
    val collectionName: String? = null,
    val collectionData: HoppCollection? = null,
    val workspaceName: String? = null,
    val collectionId: String? = null
) : ExportMetadata {
    override fun formatDisplay(): String? {
        if (collectionData != null) {
            return null
        }

        val workspaceDisplay = workspaceName
        val collectionDisplay = collectionName ?: collectionId

        return workspaceDisplay.append(collectionDisplay, separator = "/")
    }
}
