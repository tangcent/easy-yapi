package com.itangcent.easyapi.util.file

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import java.awt.datatransfer.StringSelection
import java.nio.charset.Charset

/**
 * Default implementation of FileSaveHelper.
 * Provides a user-friendly dialog for saving or copying exported content.
 * 
 * When invoked, shows a dialog with three options:
 * - Save to File: Opens file chooser dialog
 * - Copy to Clipboard: Copies content to system clipboard
 * - Cancel: Dismisses without action
 * 
 * @param outputCharset The charset to use when writing files
 */
class DefaultFileSaveHelper(private val outputCharset: Charset = Charsets.UTF_8) : FileSaveHelper {
    /**
     * Shows a dialog allowing the user to save or copy content.
     * 
     * @param content The content to save or copy
     * @param defaultFileName Suggested filename for saving
     * @param project The IntelliJ project context
     */
    override suspend fun saveOrCopy(content: String, defaultFileName: String, project: Project) {
        val choice = Messages.showYesNoCancelDialog(
            project,
            "Export output",
            "EasyAPI",
            "Save to File",
            "Copy to Clipboard",
            "Cancel",
            Messages.getQuestionIcon()
        )

        when (choice) {
            Messages.YES -> saveToFile(content, defaultFileName, project)
            Messages.NO -> copyToClipboard(content)
        }
    }

    /**
     * Opens a file chooser dialog and saves content to the selected file.
     */
    private fun saveToFile(content: String, defaultFileName: String, project: Project) {
        val descriptor = FileSaverDescriptor("Save Output", "Choose where to save the exported output")
        val chooser = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val fileWrapper = chooser.save(null as com.intellij.openapi.vfs.VirtualFile?, defaultFileName) ?: return
        fileWrapper.file.writeText(content, outputCharset)
    }

    /**
     * Copies content to the system clipboard.
     */
    private fun copyToClipboard(content: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(content))
    }
}
