package com.itangcent.easyapi.channel.apipost

import com.itangcent.easyapi.core.export.ExportMetadata

/**
 * Export metadata for the ApiPost channel, carried inside `ExportResult.Success`.
 *
 * [content] is serialized once during export (the native ApiPost document) so
 * that [ApipostChannel.handleResult] can write the file without re-serializing,
 * and so the pushed payload and the written file are byte-identical.
 *
 * There is deliberately no `document` field: the in-memory document is an
 * openapi-package type, and naming it here would leak `channel.openapi` into
 * this package's public surface (NFR-7 / AC-11).
 *
 * @property content the already-serialized native document (file-fallback path).
 * @property serverUrl the open-API base URL, shown in the result notification.
 * @property projectId the target project id, when one was resolved.
 * @property fileExport true when the export degraded to a file download because
 *   no token/project was configured; then [created] / [updated] stay 0 and
 *   [formatDisplay] returns null (nothing remote to point at).
 * @property created number of APIs newly created on the server.
 * @property updated number of existing APIs updated in place.
 */
data class ApipostExportMetadata(
    val content: String,
    val serverUrl: String? = null,
    val projectId: String? = null,
    val fileExport: Boolean = false,
    val created: Int = 0,
    val updated: Int = 0,
) : ExportMetadata {
    override fun formatDisplay(): String? = if (fileExport) null else serverUrl
}
