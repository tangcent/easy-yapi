package com.itangcent.easyapi.util.file

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Interface for selecting files and directories.
 *
 * Provides a simple API for file/directory selection dialogs.
 *
 * ## Usage
 * ```kotlin
 * val fileSelectHelper: FileSelectHelper = ...
 * val file = fileSelectHelper.selectFile("Select Config", project)
 * val dir = fileSelectHelper.selectDirectory("Select Output Folder", project)
 * ```
 *
 * @see DefaultFileSelectHelper for the default implementation
 */
interface FileSelectHelper {
    /**
     * Opens a file selection dialog.
     *
     * @param title The dialog title
     * @param project The current project
     * @return The selected file, or null if cancelled
     */
    suspend fun selectFile(title: String, project: Project): VirtualFile?

    /**
     * Opens a directory selection dialog.
     *
     * @param title The dialog title
     * @param project The current project
     * @return The selected directory, or null if cancelled
     */
    suspend fun selectDirectory(title: String, project: Project): VirtualFile?

    companion object {
        fun getInstance(project: Project): FileSelectHelper =
            DefaultFileSelectHelper()
    }
}
