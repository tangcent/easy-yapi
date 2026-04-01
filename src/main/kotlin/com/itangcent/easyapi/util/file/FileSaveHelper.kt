package com.itangcent.easyapi.util.file

import com.intellij.openapi.project.Project

/**
 * Interface for saving content to a file.
 *
 * Provides a simple API for file save operations with
 * optional copy-to-clipboard fallback.
 *
 * ## Usage
 * ```kotlin
 * val fileSaveHelper: FileSaveHelper = ...
 * fileSaveHelper.saveOrCopy(jsonContent, "api-export.json", project)
 * ```
 *
 * @see DefaultFileSaveHelper for the default implementation
 */
interface FileSaveHelper {
    /**
     * Saves content to a file or copies to clipboard.
     *
     * @param content The content to save
     * @param defaultFileName The suggested file name
     * @param project The current project
     */
    suspend fun saveOrCopy(content: String, defaultFileName: String, project: Project)

    companion object {
        fun getInstance(project: Project): FileSaveHelper =
            DefaultFileSaveHelper()
    }
}
