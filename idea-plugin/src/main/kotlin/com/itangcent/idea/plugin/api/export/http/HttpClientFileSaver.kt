package com.itangcent.idea.plugin.api.export.http

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.createDirectories
import com.intellij.util.io.readText
import com.itangcent.intellij.context.ActionContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.writeText

/**
 * Handling HTTP file saving and opening within a project.
 *
 * @author tangcent
 */
@Singleton
class HttpClientFileSaver {

    @Inject
    private lateinit var project: Project

    @Inject
    private lateinit var actionContext: ActionContext

    private val scratchesPath: Path by lazy { Paths.get(PathManager.getConfigPath(), "scratches") }

    private val localFileSystem by lazy { LocalFileSystem.getInstance() }

    /**
     * Saves the HTTP file with the specified content, derived from the provided lambda,
     * and opens the file in the editor.
     *
     * @param module The name of the module under which the file should be saved.
     * @param fileName The name of the file to be saved.
     * @param content A lambda that generates the content of the file, optionally based on the existing content.
     */
    fun saveAndOpenHttpFile(
        module: String,
        fileName: String,
        content: (String?) -> String,
    ) {
        val file = saveHttpFile(module, fileName, content)
        openInEditor(file)
    }

    /**
     * Saves the HTTP file with the specified content, derived from the provided lambda.
     *
     * @param module The name of the module under which the file should be saved.
     * @param fileName The name of the file to be saved.
     * @param content A lambda that generates the content of the file, optionally based on the existing content.
     * @return The VirtualFile object representing the saved file.
     */
    private fun saveHttpFile(
        module: String,
        fileName: String,
        content: (String?) -> String,
    ): VirtualFile {
        val file = scratchesPath.resolve(module).resolve(fileName).apply {
            parent.createDirectories()
        }
        file.writeText(content(file.takeIf { Files.exists(it) }?.readText()))

        return (localFileSystem.refreshAndFindFileByPath(file.toString())
            ?: throw IOException("Unable to find file: $file"))
    }

    /**
     * Opens the specified file in the editor.
     *
     * @param file The VirtualFile object representing the file to be opened.
     */
    private fun openInEditor(file: VirtualFile) {
        actionContext.runInWriteUI {
            FileEditorManager.getInstance(project).openFile(file, true)
        }
    }
}