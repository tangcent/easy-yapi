package com.itangcent.easyapi.core.export

/**
 * Metadata attached to an [ExportResult.Success] for display purposes.
 *
 * Implementations provide channel-specific information about the export
 * (e.g., file path, Postman collection URL) that can be shown to the user.
 */
interface ExportMetadata {

    /**
     * Returns a human-readable representation of this metadata,
     * or `null` if nothing should be displayed.
     */
    fun formatDisplay(): String?
}
