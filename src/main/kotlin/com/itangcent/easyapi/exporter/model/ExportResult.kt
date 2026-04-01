package com.itangcent.easyapi.exporter.model

/**
 * Result of an API export operation.
 *
 * Sealed class hierarchy representing the possible outcomes:
 * - [Success] - Export completed successfully
 * - [Error] - Export failed with an error message
 * - [Cancelled] - Export was cancelled by the user
 */
sealed class ExportResult {
    /**
     * Successful export result.
     *
     * @param count The number of endpoints exported
     * @param target The export target (file path, URL, etc.)
     * @param metadata Additional export metadata
     */
    data class Success(
        val count: Int,
        val target: String,
        val metadata: ExportMetadata? = null
    ) : ExportResult()
    
    /**
     * Failed export result.
     *
     * @param message The error message describing what went wrong
     */
    data class Error(val message: String) : ExportResult()
    
    /**
     * Cancelled export result (user cancelled the operation).
     */
    data object Cancelled : ExportResult()
}
